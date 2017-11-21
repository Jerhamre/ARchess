from flask import Flask
from flask_socketio import SocketIO, emit

app = Flask(__name__)
app.config['SECRET_KEY'] = 'chessMaster'
socketio = SocketIO(app)

col0 = ['WR', 'WP', '.', '.', '.', '.', 'BP', 'BR']
col1 = ['WN', 'WP', '.', '.', '.', '.', 'BP', 'BN']
col2 = ['WB', 'WP', '.', '.', '.', '.', 'BP', 'BB']
col3 = ['WQ', '.', '.', '.', '.', '.', 'BP', 'BQ']
col4 = ['WK', '.', '.', '.', '.', '.', 'BP', 'BK']
col5 = ['WB', 'WP', '.', '.', '.', '.', 'BP', 'BB']
col6 = ['WN', 'WP', '.', '.', '.', '.', 'BP', 'BN']
col7 = ['WR', 'WP', '.', '.', '.', '.', 'BP', 'BR']
chessBoard = [col0, col1, col2, col3, col4, col5, col6, col7]

allowed_castling = {'WK': True, 'BK': True, 'WRa1': True, 'WRh1': True, 'BRa8': True, 'BRh8': True}
double_step_pawn = {'pos': [-1, -1]}
waiting = {'promote': False, 'pos': [-1, -1]}
possible_promotions = ['Q', 'R', 'B', 'N']
current = {'player': 'W'}
king_info = {'W': {'x': 4, 'y': 0, 'free_space': [], 'check': False}, 'B': {'x': 4, 'y': 7, 'free_space': [], 'check': False}}

@socketio.on('board')
def board():
    print('Board')
    update_king_info(current['player'])
    emit('board', {'board': chessBoard, 'king_info': king_info}, broadcast=True)


@socketio.on('move')
def handle_move(move):
    print(move['move'])
    result = legal_move(current['player'], move['move'])
    emit('board', {'result': result, 'player': current['player'], 'board': chessBoard}, broadcast=True)


def legal_move(player, move):
    if waiting['promote']:
        if move in possible_promotions:
            chessBoard[waiting['pos'][0]][waiting['pos'][1]] = player + move
            waiting['pos'] = [-1, -1]
            waiting['promote'] = False
            next_player()
            return 'success'
        return 'invalid promotion'
    else:
        start = list(move.split('-')[0])
        end = list(move.split('-')[1])
        if start[0] == '0':
            print("castling")
            return castling(move, player)
        elif start[0] == 'K':
            print("king")
            start = coord_helper(start[1:])
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'K':
                return move_king(start, end, player)
            return 'your king is not in that position'
        elif start[0] == 'Q':
            print("queen")
            start = coord_helper(start[1:])
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'Q':
                return move_queen(start, end, player)
            return 'you don\'t have a queen in that position'
        elif start[0] == 'R':
            print("rook")
            start = coord_helper(start[1:])
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'R':
                return move_rook(start, end, player)
            return 'you don\'t have a rook in that position'
        elif start[0] == 'B':
            print("bishop")
            start = coord_helper(start[1:])
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'B':
                return move_bishop(start, end, player)
            return 'you don\'t have a bishop in that position'
        elif start[0] == 'N':
            print("knight")
            start = coord_helper(start[1:])
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'N':
                return move_knight(start, end, player)
            return 'you don\'t have a knight in that position'
        else:
            print("pawn")
            start = coord_helper(start)
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'P':
                return move_pawn(start, end, player)
            return 'you don\'t have a pawn in that position'


def castling(move, player):
    if player == 'W' and allowed_castling['WK']:
        if move == '0-0' and allowed_castling['WRh1'] and no_pieces_between([4, 0], [7, 0], False):
            update_board([4, 0], [6, 0], 'WK')
            update_board([7, 0], [5, 0], 'WR')
            allowed_castling['WK'] = False
            allowed_castling['WRh1'] = False
            next_player()
            return 'success'
        elif move == '0-0-0' and allowed_castling['WRa1'] and no_pieces_between([4, 0], [0, 0], False):
            update_board([4, 0], [2, 0], 'WK')
            update_board([0, 0], [3, 0], 'WR')
            allowed_castling['WK'] = False
            allowed_castling['WRa1'] = False
            next_player()
            return 'success'
        else:
            return 'invalid move'
    elif player == 'B' and allowed_castling['BK']:
        if move == '0-0' and allowed_castling['BRh8'] and no_pieces_between([4, 7], [7, 7], False):
            update_board([4, 7], [6, 7], 'BK')
            update_board([7, 7], [5, 7], 'BR')
            allowed_castling['BK'] = False
            allowed_castling['BRh8'] = False
            next_player()
            return 'success'
        elif move == '0-0-0' and allowed_castling['BRa8'] and no_pieces_between([4, 7], [0, 7], False):
            update_board([4, 7], [2, 7], 'BK')
            update_board([0, 7], [3, 7], 'BR')
            allowed_castling['BK'] = False
            allowed_castling['BRa8'] = False
            next_player()
            return 'success'
        else:
            return 'invalid move'
    else:
        return 'invalid move'


def move_king(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if abs(x_diff) < 2 and abs(y_diff) < 2 and on_board(end) and not_friendly_piece(end, player):
        allowed_castling[player + 'K'] = False
        update_board(start, end, player + 'K')
        next_player()
        return 'success'
    return 'illegal king move'


def move_queen(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end) and not_friendly_piece(end, player):
        if abs(x_diff) == abs(y_diff) != 0 and no_pieces_between(start, end, False):
            update_board(start, end, player + 'Q')
            next_player()
            return 'success'
        elif (x_diff == 0 != y_diff or x_diff != 0 == y_diff) and no_pieces_between(start, end, False):
            update_board(start, end, player + 'Q')
            next_player()
            return 'success'
    return 'illegal queen move'


def move_rook(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end) and not_friendly_piece(end, player) and x_diff == 0 != y_diff \
            and no_pieces_between(start, end, False):
        disable_castling(start)
        update_board(start, end, player + 'R')
        next_player()
        return 'success'
    return 'illegal rook move'


def move_bishop(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end) and not_friendly_piece(end, player) and abs(x_diff) == abs(y_diff) != 0 \
            and no_pieces_between(start, end, False):
        update_board(start, end, player + 'B')
        next_player()
        return 'success'
    return 'illegal bishop move'


def move_knight(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end) and not_friendly_piece(end, player)\
            and (abs(x_diff) == 2 and abs(y_diff) == 1 or abs(x_diff) == 1 and abs(y_diff) == 2):
        update_board(start, end, player + 'N')
        next_player()
        return 'success'
    return 'illegal knight move'


def move_pawn(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end) and not_friendly_piece(end, player):
        if x_diff == 0:
            if y_diff == player_dir(player) and no_pieces_between(start, end, True):
                update_board(start, end, player + 'P')
                if end[1] == 0 or end[1] == 7:
                    waiting['promote'] = True
                    return 'success, promote pawn'
                else:
                    next_player()
                return 'success'
            elif y_diff == 2 * player_dir(player) and first_move_pawn(start, player) and no_pieces_between(start, end, True):
                update_board(start, end, player + 'P')
                double_step_pawn['pos'] = end
                next_player()
                return 'success'
        elif abs(x_diff) == 1 and y_diff == player_dir(player):
            if opp_player(end, player):
                update_board(start, end, player + 'P')
                if end[1] == 0 or end[1] == 7:
                    waiting['promote'] = True
                    return 'success, promote pawn'
                else:
                    next_player()
                return 'success'
            elif passant(end, player):
                chessBoard[end[0]][end[1] + player_dir(player)] = '.'
                update_board(start, end, player + 'P')
                next_player()
                return 'success'
    return 'illegal pawn move'


def no_pieces_between(s, e, include_last):
    start = s.copy()
    end = e.copy()
    x_diff = 0
    y_diff = 0
    if start[0] - end[0] != 0:
        x_diff = int((start[0] - end[0])/abs(start[0] - end[0]))
    if start[1] - end[1] != 0:
        y_diff = int((start[1] - end[1])/abs(start[1] - end[1]))
    if not include_last:
        end[0] += x_diff
        end[1] += y_diff
    while start != end:
        start = [start[0] - x_diff, start[1] - y_diff]
        if chessBoard[start[0]][start[1]] != '.':
            return False
    return True


def update_board(start, end, piece):
    double_step_pawn['pos'] = [-1, -1]
    chessBoard[start[0]][start[1]] = '.'
    chessBoard[end[0]][end[1]] = piece


def disable_castling(start):
    if start == [0, 0]:
        allowed_castling['WRa1'] = False
    elif start == [7, 0]:
        allowed_castling['WRh1'] = False
    elif start == [0, 7]:
        allowed_castling['BRa8'] = False
    elif start == [7, 7]:
        allowed_castling['BRh8'] = False


def first_move_pawn(start, player):
    if start[1] == 1 and player == 'W':
        return True
    elif start[1] == 6 and player == 'B':
        return True
    return False


def passant(end, player):
    opp_pawn = [end[0], end[1] + player_dir(player)]
    if double_step_pawn['pos'] == opp_pawn and opp_player(opp_pawn, player):
            return True
    return False


def player_dir(player):
    if player == 'B':
        return 1
    return -1


def opp_player(end, player):
    if list(chessBoard[end[0]][end[1]])[0] == 'W' and player == 'B':
        return True
    elif list(chessBoard[end[0]][end[1]])[0] == 'B' and player == 'W':
        return True
    return False


def on_board(end):
    if -1 < end[0] < 8 and -1 < end[1] < 8:
        return True
    return False


def not_friendly_piece(end, player):
    if list(chessBoard[end[0]][end[1]])[0] != player:
        return True
    return False


def friendly_piece(end, player):
    return not not_friendly_piece(end, player)


def coord_helper(coord):
    coord[0] = col_number(coord[0])
    coord = [int(i)-1 for i in coord]
    return coord


def col_number(col):
    return ord(col) - ord('a') + 1


def next_player():
    current['player'] = opponent(current['player'])


def opponent(player):
    if player == 'W':
        return 'B'
    return 'W'


def update_king_info(player):
    king = king_info[player]
    king['freespace'] = []
    for i in range(-1, 2):
        for j in range(-1, 2):
            # pos = chessBoard[king['x']+i][king['y']+j]
            if on_board([king['x']+i, king['y']+j]):
                king['free_space'].append([king['x']+i, king['y']+j])
    if len(king['freespace']) != 0:
        for i in range(0, 8):
            for j in range(0, 8):
                if not_friendly_piece([i, j], player):
                    check_threatened_squares([i, j], player)
    # and (pos == '.' or not_friendly_piece([king['x']+i, king['y']+j], player))

def check_threatened_squares(pos, player):
    print('checking threatened squares')
    if chessBoard[pos[0]][pos[1]] == opponent(player) + 'Q':
        queen_threat(pos, player)


def queen_threat(pos, player):
    surrounding_squares = king_info[player]['free_space']
    for x in range(0, surrounding_squares):
        if 'success' in move_queen(pos, surrounding_squares[x], opponent(player)):
            print('todo implement')


if __name__ == '__main__':
    socketio.run(app)
