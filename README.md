# VidyoPlatfrom Screen Share Android Connector App
vidyoplatform-connector-android-virtual-video-source is a VidyoPlatform reference application highlighting how to integrate video chat into a native Android app with device screen share functionality.

Developer documentation: https://vidyo.github.io/vidyoplatform.github.io

# Clone
git clone https://github.com/Vidyo/vidyoplatform-custom-connector-android.git

## Acquire VidyoClient iOS SDK
1. Download the latest VidyoClient Android SDK package [here](https://static.vidyo.io/latest/package/VidyoClient-AndroidSDK.zip) and unzip it.
2. Copy the SDK package content located at */VidyoClient-AndroidSDK/lib/android* to the */app/libs/* folder.

#### Android Studio 3.3 | Gradle tools: 3.3.0 | Target SDK version: 28 | Min SDK 22 version: 22

- Upgrade Gradle file if neccessary
- Download required build-tools
- Sync/Clean/Build the project

### Device Screen Share Functionality Overview
- **ShareManager**: share logic controller in order to start/stop and release share components;
- **ShareSession**: responsible for setup virtual device projection and retrieve device screen image;
- **FrameProvider**: responsible for feeding VirtualVideoSorce with specified frame per rate value provided by VidyoClient library within "onVirtualVideoSourceStateUpdated" -> "VIDYO_DEVICESTATE_ConfigurationChanged";
- **FrameHolder**: raw frame holder/transmitter after initially captured image has been transformed into Bitmap;
- **ShareConstraints**: frame constraints to be configured for VitrualVideoSoruce via 'setBoundsConstraints' API.
Contains an option to limit max frame resolution. Default: Full HD (1080)
- **ShareService**: regular service as android's component for handling orientation change outside of application in order to restart share logic and update frame orientation & constraints.
