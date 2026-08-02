#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <ESP32Servo.h>

const int servoPin = 18; // サーボの信号ピン (GPIO 18)
Servo myServo;

// スマホ（LightBlue）側で設定する目印のサービスUUID
#define TARGET_SERVICE_UUID "19B10000-E8F2-537E-4F6C-D104768A1214"

// 距離（電波強度）のしきい値設定（変更なし）
const int UNLOCK_RSSI = -70; // これより大きいなら「近い」
const int LOCK_RSSI = -85;   // これより小さいなら「遠い」

int scanTime = 2; // 2秒間隔でスキャン
BLEScan* pBLEScan;

unsigned long lastSeenTime = 0; 
bool isUnlocked = false;        

void setup() {
  Serial.begin(115200);
  Serial.println("--- 傘のオートロックシステム 起動 ---");
  
  // サーボの初期設定
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  ESP32PWM::allocateTimer(3);
  myServo.setPeriodHertz(50);
  // 0度〜180度までしっかり回るようにパルス幅（500, 2400）を指定
  myServo.attach(servoPin, 500, 2400); 
  
  Serial.println("初期状態: 🔒 ロック（サーボ0度）にします");
  myServo.write(0);
  delay(500);

  // BLEスキャナーの初期設定
  BLEDevice::init("");
  pBLEScan = BLEDevice::getScan();
  pBLEScan->setActiveScan(true);
  pBLEScan->setInterval(100);
  pBLEScan->setWindow(99);
}

void loop() {
  BLEScanResults* foundDevices = pBLEScan->start(scanTime, false);
  int count = foundDevices->getCount();
  
  bool foundInThisScan = false;
  int currentRSSI = -100;

  // 検出したデバイスの中から「ターゲットのUUID」を持っているか探す
  for (int i = 0; i < count; i++) {
    BLEAdvertisedDevice device = foundDevices->getDevice(i);

    if (device.isAdvertisingService(BLEUUID(TARGET_SERVICE_UUID))) {
      foundInThisScan = true;
      currentRSSI = device.getRSSI();
      lastSeenTime = millis();
      break; 
    }
  }

  // 判定とサーボ制御ロジック
  if (foundInThisScan) {
    Serial.print("📱 スマホ発見！ RSSI: ");
    Serial.print(currentRSSI);

    if (currentRSSI >= UNLOCK_RSSI && !isUnlocked) {
      isUnlocked = true;
      Serial.println(" -> 🔓 【自動ロック解除】サーボを180度へ");
      myServo.write(180); // ここで180度まで全開！
    } else if (currentRSSI <= LOCK_RSSI && isUnlocked) {
      isUnlocked = false;
      Serial.println(" -> 🔒 【自動ロック】サーボを0度へ");
      myServo.write(0);   // ここで0度に戻す
    } else {
      Serial.println(isUnlocked ? " (🔓 解除キープ)" : " (🔒 ロックキープ)");
    }
} else {
    // 圏外処理（15秒以上連続で見失ったらロックする）
    // 6000 を 15000 に変更しました
    if (isUnlocked && (millis() - lastSeenTime > 15000)) {
      isUnlocked = false;
      Serial.println("⚠️ 電波ロスト（圏外） -> 🔒 安全のため【自動ロック】(サーボ0度)");
      myServo.write(0);
    } else if (!isUnlocked) {
      Serial.println("📡 スキャン中... (スマホが見つかりません)");
    }
  }

  pBLEScan->clearResults();   
  delay(500); 
}