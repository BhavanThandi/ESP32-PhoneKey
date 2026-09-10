package com.example.phonekey  // <-- YOUR package

import java.util.UUID

object BleUuids {
    val SERVICE: UUID = UUID.fromString("7160168f-2643-437a-a817-1afd55a0e901")
    val LOCK_STATE: UUID = UUID.fromString("7160168f-2643-437a-a817-1afd55a0e902")
    val COMMAND: UUID = UUID.fromString("7160168f-2643-437a-a817-1afd55a0e903")

    // Standard CCCD, 0x2902 expanded against the Bluetooth Base UUID
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}