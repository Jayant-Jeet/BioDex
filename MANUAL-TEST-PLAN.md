# Manual test plan

Use a fresh install or clear app data before tests that depend on first-run mobile-data consent or a new model download. The model is about 1.26 GB; keep the device connected to power and monitor available storage.

1. **Kill and reopen mid-download**
   - Start on Wi-Fi, wait for progress to advance, then force-stop BioDex from Android settings or the app info screen.
   - Reopen BioDex. Confirm the splash resumes from the existing byte count instead of restarting at 0%, then reaches verification and completion.
   - Confirm classification works with airplane mode enabled after download.
2. **Airplane mode**
   - Start on Wi-Fi and enable airplane mode during transfer.
   - Confirm the splash reports waiting for network and retains its progress. Disable airplane mode and verify automatic resume without tapping Retry.
3. **Wi-Fi to mobile data**
   - Begin on Wi-Fi, then disable Wi-Fi while mobile data is available.
   - Before the one-time mobile-data consent, confirm the transfer pauses and asks before continuing. Accept and verify it resumes without discarding the `.part` file.
   - For a clean first-run prompt, reset app data between attempts.
4. **Reboot mid-download**
   - Start a transfer, note its progress, and reboot the device.
   - Unlock the device and open BioDex. Confirm WorkManager continues the saved transfer and eventually verifies/installs the model.
5. **Low storage**
   - Use an emulator/device with less than the remaining model size plus the 256 MiB safety reserve.
   - Confirm the download does not start, the splash explains the storage requirement, and freeing space followed by Retry allows the download.
6. **Battery saver on an aggressive-OEM device**
   - On a Xiaomi/Redmi/POCO, Oppo, Vivo, Realme, Samsung, or OnePlus device, enable battery saver and begin the download.
   - Confirm the splash shows the keep-open/battery guidance and opens the system battery-optimization settings.
   - Also verify the foreground notification shows progress and Cancel; repeat with notification permission denied to ensure the in-app progress still works.
7. **Export and share to WhatsApp**
   - Open or capture a specimen and use Share in the specimen toolbar.
   - Choose WhatsApp and send the PNG to a test chat. Confirm all card edges, text, photo, rarity treatment, and shadow are present and the image opens at full resolution.
   - Repeat with a long common/scientific name and with each rarity. Verify an export error is visible if the card cannot fit fully in the app window.
