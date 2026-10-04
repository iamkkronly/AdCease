<div align="center">

# AdCease

**Block ads across your whole phone.**

Ads, trackers and malicious domains are stopped in every app and browser — not just one.
No root. No subscription. No account.

[![Download](https://img.shields.io/badge/Download-APK-1B8A3A?style=for-the-badge)](https://github.com/iamkkronly/AdCease/releases/latest)
![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

</div>

---

## What it does

Most ad blockers only work inside one browser. AdCease works everywhere on your
phone, including inside apps and games. It runs quietly in the background and you
only have to set it up once.

- **Blocks ads in every app** — not just your browser.
- **Blocks trackers** that follow you between apps.
- **Blocks known harmful sites** before they can load.
- **No root needed.** Nothing about your phone has to be modified.
- **Uses very little battery.** It only checks domain names, never your content.
- **Nothing is collected.** Your browsing never leaves your device.

## Screens

The main screen keeps it simple. One button starts and pauses protection, and
three counters show what has been happening:

| Counter | Meaning |
|---|---|
| **Total Queries** | How many lookups your phone has made |
| **Blocked** | How many of those were ads or trackers |
| **Threats Blocked** | How many were known harmful domains |

## Features

- **One-tap start and pause.** Big power button, nothing else to learn.
- **Simple setup.** Four short screens the first time you open the app.
- **Filters update on their own** every 6 hours in the background.
- **Auto-reconnect.** If protection ever drops, it turns itself back on.
- **Starts after restart.** Turn your phone off and on, and it is still working.

## Install

1. Download the APK from the [latest release](https://github.com/iamkkronly/AdCease/releases/latest).
2. Open the file on your phone. Android will ask you to allow installs from your
   file manager or browser — allow it.
3. Open AdCease and follow the four setup screens.
4. Tap **OK** on the confirmation box Android shows. Without this, the app cannot
   block anything.

That is it. The button turns green and you are protected.

> The release APK is signed with a debug key, which is normal for apps shared
> outside the Play Store. Android may warn you about an unknown developer.

## Permissions and why

| Permission | Reason |
|---|---|
| Internet | To fetch filter updates |
| Network state | To know when to reconnect |
| Foreground service | To keep protection running |
| Notifications | To show whether protection is on |
| Start on boot | To turn back on after a restart |
| Ignore battery optimization | So battery saver cannot silently disable it |

## Privacy

AdCease does not collect your browsing. There is no account, no sign-in and no
history stored anywhere. The counters on the main screen are kept on your phone
only, and you can clear them any time with **Reset statistics**.

## Building from source

Requires JDK 11 and the Android SDK (platform 33, build-tools 33.0.2).

```bash
git clone https://github.com/iamkkronly/AdCease.git
cd AdCease
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

## License

MIT — see [LICENSE](LICENSE).

---

<div align="center">

Made by **Kaustav Kanti Ray** · [@iamkkronly](https://github.com/iamkkronly)

</div>
