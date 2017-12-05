from flask import Flask
from flask_socketio import SocketIO, emit

app = Flask(__name__)
app.config['SECRET_KEY'] = 'chessMaster'
socketio = SocketIO(app)

col0 = ['WR', 'WP', '.', '.', '.', '.', 'BP', 'BR']
col1 = ['WN', 'WP', '.', '.', '.', '.', 'BP', 'BN']
col2 = ['WB', 'WP', '.', '.', '.', '.', 'BP', 'BB']
col3 = ['WQ', 'WP', '.', '.', '.', '.', 'BP', 'BQ']
col4 = ['WK', 'WP', '.', '.', '.', '.', 'BP', 'BK']
col5 = ['WB', 'WP', '.', '.', '.', '.', 'BP', 'BB']
col6 = ['WN', 'WP', '.', '.', '.', '.', 'BP', 'BN']
col7 = ['WR', 'WP', '.', '.', '.', '.', 'BP', 'BR']
chessBoard = [col0, col1, col2, col3, col4, col5, col6, col7]

allowed_castling = {'WK': True, 'BK': True, 'WRa1': True, 'WRh1': True, 'BRa8': True, 'BRh8': True}
double_step_pawn = {'pos': [-1, -1]}
waiting = {'promote': False, 'pos': [-1, -1]}
possible_promotions = ['Q', 'R', 'B', 'N']
current = {'player': 'W'}
players = {'W': '', 'B': ''}
king_info = {'W': {'x': 4, 'y': 3, 'free_space': [], 'check': False, 'save_king': [], 'checkmate': False},
             'B': {'x': 4, 'y': 7, 'free_space': [], 'check': False, 'save_king': [], 'checkmate': False}}


@socketio.on('join')
def join(player):
    print('player joined ', player)
    you = 'observer'
    if players['W'] == '' or players['W'] == player['user']:
        players['W'] = player['user']
        you = 'W'
    elif players['B'] == '' or players['B'] == player['user']:
        players['B'] = player['user']
        you = 'B'
    if players['W'] != '' and players['B'] != '':
        emit('joined', {'started': "true", 'you': you})
        emit('board', {'player': current['player'], 'board': chessBoard, 'king_info': king_info}, broadcast=True)
    else:
        emit('joined', {'started': "false", 'you': you})


@socketio.on('board')
def board():
    print('Board')
    update_king_info(current['player'])
    update_king_info(opponent(current['player']))
    emit('board', {'board': chessBoard, 'king_info': king_info}, broadcast=True)


@socketio.on('move')
def handle_move(move):
    print(move['move'], 'user: ', move['user'], 'white: ', players['W'], 'black ', players['B'])
    if players[current['player']] == move['user']:
        result = legal_move(current['player'], move['move'])
        if 'success' in result:
            king_info[current['player']]['check'] = False
            update_king_info(current['player'])
            update_king_info(opponent(current['player']))
            if 'promote' in result:
                emit('board', {'result': result, 'player': current['player'], 'board': chessBoard, 'king_info': king_info}, broadcast=True)
            else:
                emit('board', {'result': result, 'player': current['player'], 'board': chessBoard, 'king_info': king_info}, broadcast=True)

        else:
            emit('moveFailed', {'result': result})
    else:
        emit('moveFailed', {'result': 'it\'s not your move'})


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
            if chessBoard[start[0]][start[1]] == player + 'K' and not_friendly_piece(end, player):
                result = move_king(start, end, player)
                if 'success' in result:
                    update_board(start, end, player + 'K')
                    king_info[player]['x'] = end[0]
                    king_info[player]['y'] = end[1]
                    if check(player):
                        king_info[player]['x'] = start[0]
                        king_info[player]['y'] = start[1]
                        update_board(end, start, player + 'K')
                        return 'illegal, your king would be in check after that move'
                    else:
                        allowed_castling[player + 'K'] = False
                        next_player()
                return result
            return 'illegal, your king is not in that position'
        elif start[0] == 'Q':
            print("queen")
            start = coord_helper(start[1:])
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'Q' and not_friendly_piece(end, player):
                result = move_queen(start, end, player)
                if 'success' in result:
                    update_board(start, end, player + 'Q')
                    if check(player):
                        update_board(end, start, player + 'Q')
                        return 'your king would be in check after that move'
                    else:
                        next_player()
                return result
            return 'you don\'t have a queen in that position'
        elif start[0] == 'R':
            print("rook")
            start = coord_helper(start[1:])
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'R' and not_friendly_piece(end, player):
                result = move_rook(start, end, player)
                if 'success' in result:
                    update_board(start, end, player + 'R')
                    if check(player):
                        update_board(end, start, player + 'R')
                        return 'your king would be in check after that move'
                    else:
                        disable_castling(start)
                        next_player()
                return result
            return 'you don\'t have a rook in that position'
        elif start[0] == 'B':
            print("bishop")
            start = coord_helper(start[1:])
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'B' and not_friendly_piece(end, player):
                result = move_bishop(start, end, player)
                if 'success' in result:
                    update_board(start, end, player + 'B')
                    if check(player):
                        update_board(end, start, player + 'B')
                        return 'your king would be in check after that move'
                    else:
                        next_player()
                return result
            return 'you don\'t have a bishop in that position'
        elif start[0] == 'N':
            print("knight")
            start = coord_helper(start[1:])
            end = coord_helper(end)
            if chessBoard[start[0]][start[1]] == player + 'N' and not_friendly_piece(end, player):
                result = move_knight(start, end, player)
                if 'success' in result:
                    update_board(start, end, player + 'N')
                    if check(player):
                        update_board(end, start, player + 'N')
                        return 'your king would be in check after that move'
                    else:
                        next_player()
                return result
            return 'you don\'t have a knight in that position'
        else:
            print("pawn")
            start = coord_helper(start)
            end = coord_helper(end)
            print('piece ', chessBoard[start[0]][start[1]])
            print('player', player)
            print ('not_friendly_piece ', not_friendly_piece(end, player))
            if chessBoard[start[0]][start[1]] == player + 'P' and not_friendly_piece(end, player):
                result = move_pawn(start, end, player)
                if 'success' in result:
                    update_board(start, end, player + 'P')
                    if check(player):
                        update_board(end, start, player + 'P')
                        return 'your king would be in check after that move'
                    elif not waiting['promote']:
                        next_player()
                return result
            return 'you don\'t have a pawn in that position'


def castling(move, player):
    if player == 'W' and allowed_castling['WK']:
        if move == '0-0' and allowed_castling['WRh1'] and no_pieces_between([4, 0], [7, 0], False, player):
            update_board([4, 0], [6, 0], 'WK')
            update_board([7, 0], [5, 0], 'WR')
            allowed_castling['WK'] = False
            allowed_castling['WRh1'] = False
            next_player()
            return 'success'
        elif move == '0-0-0' and allowed_castling['WRa1'] and no_pieces_between([4, 0], [0, 0], False, player):
            update_board([4, 0], [2, 0], 'WK')
            update_board([0, 0], [3, 0], 'WR')
            allowed_castling['WK'] = False
            allowed_castling['WRa1'] = False
            next_player()
            return 'success'
        else:
            return 'invalid move'
    elif player == 'B' and allowed_castling['BK']:
        if move == '0-0' and allowed_castling['BRh8'] and no_pieces_between([4, 7], [7, 7], False, player):
            update_board([4, 7], [6, 7], 'BK')
            update_board([7, 7], [5, 7], 'BR')
            allowed_castling['BK'] = False
            allowed_castling['BRh8'] = False
            next_player()
            return 'success'
        elif move == '0-0-0' and allowed_castling['BRa8'] and no_pieces_between([4, 7], [0, 7], False, player):
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
    if abs(x_diff) < 2 and abs(y_diff) < 2 and on_board(end):
        return 'success'
    return 'illegal king move'


def move_queen(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end):
        if abs(x_diff) == abs(y_diff) != 0 and no_pieces_between(start, end, False, player):
            return 'success'
        elif (x_diff == 0 != y_diff or x_diff != 0 == y_diff) and no_pieces_between(start, end, False, player):
            return 'success'
    return 'illegal queen move'


def move_rook(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end) and (x_diff == 0 != y_diff or y_diff == 0 != x_diff)\
            and no_pieces_between(start, end, False, player):
        return 'success'
    return 'illegal rook move'


def move_bishop(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end) and abs(x_diff) == abs(y_diff) != 0 and no_pieces_between(start, end, False, player):
        return 'success'
    return 'illegal bishop move'


def move_knight(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end) and (abs(x_diff) == 2 and abs(y_diff) == 1 or abs(x_diff) == 1 and abs(y_diff) == 2):
        return 'success'
    return 'illegal knight move'


def move_pawn(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if on_board(end):
        if x_diff == 0:
            if y_diff == player_dir(player) and no_pieces_between(start, end, True, player):
                update_board(start, end, player + 'P')
                if end[1] == 0 or end[1] == 7:
                    waiting['promote'] = True
                    return 'success, promote pawn'
                return 'success'
            elif y_diff == 2 * player_dir(player) and first_move_pawn(start, player) and no_pieces_between(start, end, True, player):
                update_board(start, end, player + 'P')
                double_step_pawn['pos'] = end
                return 'success'
        elif abs(x_diff) == 1 and y_diff == player_dir(player):
            if opp_piece(end, player):
                update_board(start, end, player + 'P')
                if end[1] == 0 or end[1] == 7:
                    waiting['promote'] = True
                    return 'success, promote pawn'
                return 'success'
            elif passant(end, player):
                chessBoard[end[0]][end[1] + player_dir(player)] = '.'
                update_board(start, end, player + 'P')
                return 'success'
    return 'illegal pawn move'


def no_pieces_between(s, e, include_last, player):
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
        if chessBoard[start[0]][start[1]] != '.' and chessBoard[start[0]][start[1]] != opponent(player) + 'K':
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
    if double_step_pawn['pos'] == opp_pawn and opp_piece(opp_pawn, player):
            return True
    return False


def player_dir(player):
    if player == 'B':
        return 1
    return -1


def opp_piece(end, player):
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
    if list(chessBoard[end[0]][end[1]])[0] == opponent(player) or list(chessBoard[end[0]][end[1]])[0] == '.':
        return True
    return False


def friendly_piece(end, player):
    if list(chessBoard[end[0]][end[1]])[0] == player:
        return True
    return False


def coord_helper(coord):
    coord[0] = col_number(coord[0])
    coord = [int(i)-1 for i in coord]
    return coord


def col_number(col):
    return ord(col) - ord('a') + 1


def next_player():
    print('current', current['player'])
    current['player'] = opponent(current['player'])
    print('next', current['player'])


def opponent(player):
    if player == 'W':
        return 'B'
    return 'W'


def update_king_info(player):
    king = king_info[player]
    king['free_space'] = []
    king['save_king'] = []
    for i in range(-1, 2):
        for j in range(-1, 2):
            if on_board([king['x']+i, king['y']+j]):
                king['free_space'].append([king['x']+i, king['y']+j])
    if len(king['free_space']) != 0:
        for i in range(0, 8):
            for j in range(0, 8):
                if not_friendly_piece([i, j], player):
                    check_threatened_squares([i, j], player)


def check_threatened_squares(pos, player):
    if chessBoard[pos[0]][pos[1]] == opponent(player) + 'Q':
        threat(pos, player, move_queen)
    elif chessBoard[pos[0]][pos[1]] == opponent(player) + 'R':
        threat(pos, player, move_rook)
    elif chessBoard[pos[0]][pos[1]] == opponent(player) + 'B':
        threat(pos, player, move_bishop)
    elif chessBoard[pos[0]][pos[1]] == opponent(player) + 'N':
        threat(pos, player, move_knight)
    elif chessBoard[pos[0]][pos[1]] == opponent(player) + 'P':
        threat(pos, player, threat_pawn)


def threat(pos, player, move_fun):
    king = king_info[player]
    surrounding_squares = king['free_space']
    remove = []
    for x in range(0, len(surrounding_squares)):
        if 'success' in move_fun(pos, surrounding_squares[x], opponent(player)):
            remove.append(x)
    for x in range(len(remove)-1, -1, -1):
        del surrounding_squares[remove[x]]
    if 'success' in move_fun(pos, [king['x'], king['y']], opponent(player)):
        king['check'] = True
        if list(chessBoard[pos[0]][pos[1]])[1] == 'N':
            king['save_king'].append([pos])
        else:
            king['save_king'].append(squares_between(pos, [king['x'], king['y']]))
    blocked_by_friendly(surrounding_squares, player)
    if len(king['free_space']) == 0 and king['check']:
        saved = save_king(king['save_king'], player)
        if not saved:
            king['checkmate'] = True


def save_king(threats, player):
    if len(threats) > 1:
        return False
    for i in range(0, 8):
        for j in range(0, 8):
            if friendly_piece([i, j], player):
                if chessBoard[i][j] == player + 'Q':
                    if save_helper([i, j], threats[0], move_queen, player, 'Q'):
                        return True
                elif chessBoard[i][j] == player + 'R':
                    if save_helper([i, j], threats[0], move_rook, player, 'R'):
                        return True
                elif chessBoard[i][j] == player + 'B':
                    if save_helper([i, j], threats[0], move_bishop, player, 'B'):
                        return True
                elif chessBoard[i][j] == player + 'N':
                    if save_helper([i, j], threats[0], move_knight, player, 'N'):
                        return True
                elif chessBoard[i][j] == player + 'P':
                    if save_helper([i, j], threats[0], threat_pawn, player, 'P'):
                        return True
    return False


def save_helper(start, block_squares, move_fun, player, piece):
    print(block_squares)
    for x in range(0, len(block_squares)):
        print(block_squares[x])
        if 'success' in move_fun(start, block_squares[x], player):
            board_copy = chessBoard.copy()
            update_board(start, block_squares[x], player + piece)
            if not check(player):
                restore_board(board_copy)
                return True
            restore_board(board_copy)
    return False


def restore_board(board_copy):
    chessBoard[0] = board_copy[0]
    chessBoard[1] = board_copy[1]
    chessBoard[2] = board_copy[2]
    chessBoard[3] = board_copy[3]
    chessBoard[4] = board_copy[4]
    chessBoard[5] = board_copy[5]
    chessBoard[6] = board_copy[6]
    chessBoard[7] = board_copy[7]


def blocked_by_friendly(surrounding_squares, player):
    remove = []
    for x in range(0, len(surrounding_squares)):
        if friendly_piece(surrounding_squares[x], player):
            remove.append(x)
    for x in range(len(remove) - 1, -1, -1):
        del surrounding_squares[remove[x]]


def check(player):
    for i in range(0, 8):
        for j in range(0, 8):
            if friendly_piece([i, j], opponent(player)):
                if check_helper([i, j], player):
                    return True
    return False


def check_helper(pos, player):
    king = king_info[player]
    if chessBoard[pos[0]][pos[1]] == opponent(player) + 'Q':
        if 'success' in move_queen(pos, [king['x'], king['y']], opponent(player)):
            return True
    elif chessBoard[pos[0]][pos[1]] == opponent(player) + 'R':
        if 'success' in move_rook(pos, [king['x'], king['y']], opponent(player)):
            return True
    elif chessBoard[pos[0]][pos[1]] == opponent(player) + 'B':
        if 'success' in move_bishop(pos, [king['x'], king['y']], opponent(player)):
            return True
    elif chessBoard[pos[0]][pos[1]] == opponent(player) + 'N':
        if 'success' in move_knight(pos, [king['x'], king['y']], opponent(player)):
            return True
    elif chessBoard[pos[0]][pos[1]] == opponent(player) + 'P':
        if 'success' in threat_pawn(pos, [king['x'], king['y']], opponent(player)):
            return True
    return False


def threat_pawn(start, end, player):
    x_diff = start[0] - end[0]
    y_diff = start[1] - end[1]
    if abs(x_diff) == 1 and y_diff == player_dir(player):
        return 'success'
    return 'fail'


def squares_between(s, e):
    squares = []
    start = s.copy()
    end = e.copy()
    x_diff = 0
    y_diff = 0
    if start[0] - end[0] != 0:
        x_diff = int((start[0] - end[0]) / abs(start[0] - end[0]))
    if start[1] - end[1] != 0:
        y_diff = int((start[1] - end[1]) / abs(start[1] - end[1]))
    while start != end:
        squares.append(start)
        start = [start[0] - x_diff, start[1] - y_diff]
    return squares


if __name__ == '__main__':
    socketio.run(app, host='0.0.0.0', port=5000)
