# Server
## Installation instructions
1. Clone the server branch
2. Install a Python 3.x compiler
3. Install Python pip (will probably be installed when installing the Python compiler)
4. Run pip install flask and pip install flask-socketio
5. Run server.py

## Test the server
The server can be tested using the simple html files in this branch. Change the IP in the io.connect() call if you are not testing on the same computer. Opening the html files in your browser will result in 2 different users joining a room called 'thisRoom'. The information retrieved from the server will be displayed in the log. Moves can be made by typing them in the text box and pressing the 'Send' button, the 'board' button is used to get some information from the server without submitting a move.
