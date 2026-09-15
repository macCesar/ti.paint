# Change Log (iOS)
<pre>
v3.0.0 	MAJOR RELEASE: Complete feature overhaul with cross-platform parity
	- Cross-platform undo/redo implementation (matching Android functionality)
	- Complete playback drawing system with movie mode support
	- Pause/resume/stop playback controls with speed control
	- Progress tracking for playback operations
	- getStrokesData() and loadStrokes() for session persistence
	- FIXED: Erase mode now properly shows strokeColor during drawing
	- Improved memory efficiency with stroke-based rendering

v1.4.1 	Added support for touchstart, touchend, touchcancel, touchmove [MOD-2070]

v1.4.0 	Updated project with 64bit binary support [TIMOB-18092]

v1.3	Rewrote rendering algorithm to allow for a number of enhancements:
		- Retina display support [MOD-636]
		- Fix erratic sharpening of previous drawings [MOD-635]
		- Smooth erasing identical to how Paint behaves on Android
		- Improved multi-touch drawing performance
		- Quadratic smoothing of lines

v1.2	Fixed percent width visual defects [MOD-348]
		Improved multi-touch drawing performance

v1.1	Added multi-touch support [MOD-243]
		Added "image" property to the paint view. See example and documentation to find out more.

v1.0    Initial Release


# Change Log (android)
<pre>
v6.1.0  Rebuilt with Titanium SDK 13.4.1 (NDK 28.1) for 16 KB memory page size support
	- 64-bit native libraries (arm64-v8a, x86_64) aligned to 16 KB, required by Google Play
	- Minimum Titanium SDK raised to 13.0.0

v6.0.0  MAJOR RELEASE: Advanced playback system and stroke persistence
	- Enhanced existing undo/redo system for cross-platform consistency
	- Complete playback drawing system with movie mode support
	- Pause/resume/stop playback controls with speed control
	- Configurable playback speed and progress tracking
	- getStrokesData() and loadStrokes() for session save/load functionality
	- Thread-safe UI operations and improved performance

v2.0.2  [MOD-2167] Recompiled binary for Android 6.0 support

v2.0.2	Building with 2.1.3.GA to support x86 devices

v2.0.1	Fixed a couple multi-touch scenarios that were drawing erratic lines [MOD-638]

v2.0	Upgraded to module api version 2 for 1.8.0.1

v1.1	Added multi-touch support [MOD-243]
		Added "image" property to the paint view. See example and documentation to find out more.

v1.0    Initial Release
