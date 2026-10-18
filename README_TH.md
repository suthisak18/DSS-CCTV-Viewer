# DSS CCTV Viewer — Android MVP

โปรเจกต์เริ่มต้นสำหรับ Android เพื่อค้นหาอุปกรณ์ ONVIF ใน LAN และเล่นสตรีม RTSP หลายกล้องด้วย LibVLC

## สิ่งที่ทำได้ในเวอร์ชัน 0.1.0
- ส่ง ONVIF WS-Discovery Probe ไปยัง multicast `239.255.255.250:3702`
- แสดง IP และ XAddr ที่ตอบกลับ
- เพิ่มกล้องด้วย IP และ RTSP URL
- แสดงวิดีโอหลายรายการในหน้าเดียว
- บันทึกรายการกล้องใน SharedPreferences

## ข้อจำกัดที่ควรทราบ
- Discovery เป็นเพียงการค้นหาอุปกรณ์ ยังไม่ได้เรียก ONVIF SOAP `GetProfiles` / `GetStreamUri` อัตโนมัติ
- หลังค้นพบอุปกรณ์ ต้องระบุ RTSP URL ด้วยตนเองใน MVP นี้ รูปแบบ URL แตกต่างตามผู้ผลิต/รุ่น/การตั้งค่ากล้อง
- การล็อกอิน RTSP แตกต่างตามกล้องและ firmware; ต้องทดสอบกับกล้องจริง
- ข้อมูลกล้องรวมถึงรหัสผ่านในเวอร์ชันนี้เก็บใน SharedPreferences แบบไม่เข้ารหัส เหมาะสำหรับต้นแบบทดสอบเท่านั้น ห้ามใช้เก็บบัญชีสำคัญในงานจริง
- การดูจากภายนอกเครือข่ายให้เชื่อม VPN เข้าสู่ LAN ก่อน ไม่ควรเปิด RTSP port ตรงสู่อินเทอร์เน็ต
- การดูหลายกล้องพร้อมกันใช้ CPU/RAM/แบตเตอรี่สูง จำนวนที่ลื่นไหลขึ้นกับมือถือ ความละเอียด bitrate และ codec
- ต้องทดสอบกับอุปกรณ์จริงก่อนรับรองว่า build/stream ใช้ได้ทุกยี่ห้อ

## Build APK ด้วย Android Studio
1. ติดตั้ง Android Studio รุ่นปัจจุบัน และ Android SDK Platform 35
2. แตก ZIP นี้
3. Android Studio > Open > เลือกโฟลเดอร์ `DSS_CCTV_Viewer`
4. ตั้ง JDK/Gradle JDK เป็น JDK 17
5. รอ Gradle Sync และแก้ dependency/network issue ถ้ามี
6. เลือก Build > Build Bundle(s) / APK(s) > Build APK(s)
7. APK debug จะอยู่ที่ `app/build/outputs/apk/debug/app-debug.apk`

## ทดสอบจริง
1. ต่อมือถือกับ Wi-Fi วงเดียวกับกล้อง และตรวจว่าไม่มี client isolation/VLAN firewall กั้น multicast
2. เปิด ONVIF ในกล้องและสร้างบัญชี ONVIF ตามคู่มือผู้ผลิต หากจำเป็น
3. กด “ค้นหา ONVIF” แล้วเลือกอุปกรณ์ที่พบ
4. ใส่ RTSP URL, username และ password ที่ถูกต้อง แล้วบันทึก
5. ทดสอบกล้องทีละตัวก่อนเพิ่มหลายตัว
6. หากภาพไม่ขึ้น ให้ตรวจ IP/port 554, สิทธิ์ RTSP, URL path, H.264/H.265, firewall และทดสอบ URL เดียวกันด้วย VLC บนคอมพิวเตอร์
7. ทดสอบนอกสถานที่โดยเชื่อม VPN เข้าสู่ LAN แล้วใช้ IP/RTSP URL ภายในเครือข่าย

## ตัวอย่างสำหรับ Hikvision
สตรีมหลักที่พบบ่อยคือ:
`rtsp://CAMERA_IP:554/Streaming/Channels/101`

สตรีมย่อยที่พบบ่อยคือ:
`rtsp://CAMERA_IP:554/Streaming/Channels/102`

URL เป็นตัวอย่างเท่านั้น ต้องตรวจสอบตามรุ่น firmware และการตั้งค่าจริง อย่าใส่รหัสผ่านลงใน URL ที่แชร์หรือส่งต่อ

## Next steps สำหรับรุ่นใช้งานจริง
- เพิ่ม ONVIF SOAP GetCapabilities/GetProfiles/GetStreamUri และการยืนยันตัวตน WS-Security/HTTP Digest
- เก็บรหัสผ่านด้วย Android Keystore / encrypted storage
- เพิ่มหน้ารายละเอียดการเชื่อมต่อและบันทึก log ที่ไม่เปิดเผยรหัสผ่าน
- เพิ่มการเชื่อมต่อ VPN guidance, reconnect, stream selection, portrait/landscape และ lifecycle management
- เพิ่ม UI สำหรับแก้ไข/ลบ/จัดเรียงกล้องและจำกัดจำนวนสตรีมพร้อมกัน
