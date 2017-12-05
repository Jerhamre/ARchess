# ARchess / AR Core & Rendering Pipeline

## Compile Instructions
1. Download the ARCore SDK from https://developers.google.com/ar/develop/java/getting-started.
2. Place the project folder (ARchess-ar_render) inside the SDK samples folder (arcore-android-sdk-master\samples). The path should look like this **'arcore-android-sdk-master\samples\ARchess-ar_render'**
3. Open ARchess-ar_render in Android Studio and compile as normal.

## ARCore demo
This project is based on the ARCore development demo available at https://developers.google.com/ar/develop/java/getting-started.
It currently rely heavily on the rendering pipeline that comes with the demo and there shouldn't be any major changes to the 
API in terms of integration.

## API
### Input
Demo comes with touch input to create new entities on screen.

### Chess Board
The main activity currently hold a private int[8][8] grid to render the chess board.
