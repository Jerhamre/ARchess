# ARchess / AR Core & Rendering Pipeline

## ARCore demo
This project is based on the ARCore development demo available at https://developers.google.com/ar/develop/java/getting-started.
It currently rely heavily on the rendering pipeline that comes with the demo and there shouldn't be any major changes to the 
API in terms of integration.

## API
### Input
Demo comes with touch input to create new entities on screen.

### Chess Board
The main activity currently hold a private int[8][8] grid to render the chess board.
