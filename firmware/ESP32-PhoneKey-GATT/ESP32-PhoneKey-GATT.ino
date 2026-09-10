#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Your three UUIDs from the contract
#define SERVICE_UUID      "7160168f-2643-437a-a817-1afd55a0e901"
#define LOCKSTATE_UUID    "7160168f-2643-437a-a817-1afd55a0e902"
#define COMMAND_UUID      "7160168f-2643-437a-a817-1afd55a0e903"

// Pointers you'll need to store as you build the tree
BLEServer* pServer = nullptr;
BLECharacteristic* pLockState = nullptr;
BLECharacteristic* pCommand = nullptr;

uint8_t currentState = 0;   // 0 = LOCKED, 1 = UNLOCKED
constexpr uint8_t ledPin = 2;

class CommandCallbacks : public BLECharacteristicCallbacks {
  public:
  void onWrite(BLECharacteristic* pCharacteristic) override{
    // 1. Get the incoming bytes and their length
    uint8_t* pBytes = pCharacteristic->getData();
    size_t pLength = pCharacteristic->getLength();
    // 2. Guard: if length is 0, return — nothing to read
    if (pLength == 0) {
      return;
    }
    // 3. Read the first byte
    uint8_t firstByte = *pBytes;
    // 4. Guard: if it isn't 0 or 1, return — ignore garbage
    if (firstByte != 0 && firstByte !=1) {
      return;
    }
    // 5. If it matches currentState, return — no change, no notify
    if (currentState == firstByte) {
      return;
    }
    // 6. Update currentState
    currentState = firstByte;
    // 7. Drive the LED to match
    digitalWrite(ledPin, currentState);
    // 8. Write currentState into pLockState
    pLockState->setValue(&currentState, 1);
    // 9. Notify
    pLockState->notify();
    Serial.print("Received: "); Serial.println(firstByte);
  }
};

class ServerCallbacks : public BLEServerCallbacks {
public:
  void onConnect(BLEServer* pServer) override {
    // optional: a Serial.println so you can see connections happen
    Serial.println("Connected");
  }

  void onDisconnect(BLEServer* pServer) override {
    // 1. Serial.println so you can see the disconnect
    Serial.println("Disconnected");
    // 2. Restart advertising so the device is findable again
    pServer->startAdvertising();
    Serial.println("Advertising as ESP32-PhoneKey");
  }
};

void setup() {
  Serial.begin(115200);

  pinMode(ledPin, OUTPUT);
  // PHASE 1 — Build the tree
  // 1. Init the stack with your broadcast name
  BLEDevice::init("ESP32-PhoneKey");
  // 2. Create the server (store in pServer)
  pServer = BLEDevice::createServer();
  // 3. Create the service off the server (local BLEService* is fine)
  BLEService* pService = pServer->createService(SERVICE_UUID);
  pServer->setCallbacks(new ServerCallbacks());
  // 4. Create lockState characteristic (READ + NOTIFY) — store in pLockState
  pLockState = pService->createCharacteristic(LOCKSTATE_UUID, (BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY));
  // 5. Create command characteristic (WRITE) — store in pCommand
  pCommand = pService->createCharacteristic(COMMAND_UUID, BLECharacteristic::PROPERTY_WRITE);
  pCommand->setCallbacks(new CommandCallbacks());
  // PHASE 2 — Starting value + publish
  // 6. Give pLockState its initial byte (0 = LOCKED)
  uint8_t lockedValue = 0;
  pLockState->setValue(&lockedValue, 1);
  pLockState->addDescriptor(new BLE2902());
  // 7. Start the service
  pService->start();
  // PHASE 3 — Announce
  // 8. Get advertising object, add your service UUID, start advertising
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->start();
  Serial.println("Advertising as ESP32-PhoneKey");
}

void loop() {
  // Empty — the BLE stack runs on its own
}