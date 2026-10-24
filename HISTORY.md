# Changelog

### v1.0.0-kk (R36S 4:3 KitKat Edition)
* Aggressively optimized for Android 4.4 KitKat on 4:3 screens (such as R36S handheld console, 640x480).
* Fixed screen glitching and tearing when switching aspect ratios and changing channels.
* Fixed video stream dropping / shutting off after playback startup by handling live window drift and startup timeout cancellations.
* Added native wake lock protection to maintain smooth live IPTV streaming.
* Fixed aspect ratio handling with proper letterboxing / pillarboxing / zoom / stretch modes.
* Advanced playlist manager with file chooser import, highlighted buttons, and high-contrast white accents.
* Suppressed transient EPG loading error toasts on broken playlist metadata.
* Removed donation button and cleaned up menus for lean navigation.
* Multi-architecture release builds: armeabi-v7a, arm64-v8a, x86, x86_64, and universal.

### v1.3.7.18-kitkat
* Trimmed APK size, retained optimized armv7 native libraries.

### v1.3.7.17-kitkat
* Fixed version check update logic.

### v1.3.7.16-kitkat
* Optimized playback black screen transition.
* Upgraded to support multiple source lines per channel.

### v1.3.7.15-kitkat
* Default source configured with official stable feeds.

### v1.3.7.11
* Fixed crash issues.
* Fixed menu layout issues on select devices.

### v1.3.7.6
* Added error reporting.

### v1.3.7
* Time display shows seconds.
* Added support for 3-digit channel numbers.

### v1.3.6
* Resolved startup white screen issue.
* Automatically plays default channel after restoring configuration.
* Fixed channel memory persistence failure.
* Fixed menu selection issue in certain edge cases.
* Configurable EPG URL.

### v1.3.5-kk
* Merged upstream updates.
* Improved Android 4.4 KitKat compatibility.
* Updated proxy endpoints.

### v1.3.5
* Configurable compact menu mode.
* Additional detail optimizations.

### v1.3.4
* Visual styling enhancements.

### v1.3.3
* Distinct group channel merging fixes.
* Prevent channel file overwrite.
* Fixed issue where all channels were not displayed.

### v1.3.2-kk
* Improved application startup speed.
* Detect and display IPv6 support.
* Debug metadata logging.
* Dependency updates.

### v1.3.2
* Fixed remote configuration port to 34567.
* Optimized web configuration UI styling.
* Configurable display of "All Channels".

### v1.3.1
* Immediately refresh video sources after restoring defaults.

### v1.3.0
* Fixed favorites persistence bug.

### v1.2.9
* Merged multi-address sources for identical channels.
* Upgraded default sources.
* In favorite mode, up/down keys cycle only within favorite channels.

### v1.2.8-kk
* Improved playback compatibility and performance on legacy hardware.
* Android KitKat TLS/HTTPS modern cipher suite backports.
* Fixed dual-start bug on certain TV sets.

### v1.2.8
* Fixed playback failures on specific stream formats.
* Fixed random crash issues.

### v1.2.7
* Added initial EPG support.
* Category layout enhancements.

### v1.2.6
* Fixed black screen issue when changing channels.
* Fixed configuration address request timeouts.
* Added proxy server configuration support.

### v1.2.5
* Configuration URL compatibility improvements.
* Mobile device aspect ratio adjustments.
* D-Pad Left key behavior optimized.

### v1.2.3
* Fixed playback issues on complex media feeds.

### v1.2.2
* Channel list layout optimization.

### v1.2.0
* Fixed local IP address discovery.
* Clearing favorites on configuration reset.
* Mobile favorites support.

### v1.1.7
* QR code remote management.
* Custom HTTP headers support.
* DASH streaming support.

### v1.1.5
* Embedded local web server for LAN configuration on port 34567.

### v1.0.0
* Initial release.
