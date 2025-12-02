import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private int port;
    private Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Chat server started on port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket, this);
            clients.add(handler);
            handler.start();
        }
    }

    public void broadcastText(String sender, String message) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendText(sender, message);
            }
        }
    }

    // [추가] 전체에게 시스템 메시지 전송
    public void broadcastSystem(String message) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendSystemMessage(message);
            }
        }
    }

    // [추가] 귓속말 전송 (특정 대상에게만 전송)
    public void sendWhisper(String sender, String targetName, String message) {
        synchronized (clients) {
            boolean found = false;
            for (ClientHandler c : clients) {
                // ClientHandler에 getUserName()을 만들거나, userName 변수를 public으로 변경 필요
                // 여기서는 아래 (3)번에서 getUserName()을 추가한다고 가정
                if (c.getUserName().equals(targetName)) {
                    c.sendWhisperPacket(sender, message);
                    found = true;
                    break;
                }
            }
            // (선택사항) 보낸 사람에게 '전송 완료' 알림을 주고 싶으면 여기서 처리 가능
        }
    }

    public void broadcastImage(String sender, String fileName, byte[] data) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendImage(sender, fileName, data);
            }
        }
    }

    public void removeClient(ClientHandler c) {
        clients.remove(c);
    }

    public static void main(String[] args) {
        int port = 5000; // 기본 포트
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        try {
            new ChatServer(port).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 클라이언트 하나 담당하는 스레드
    static class ClientHandler extends Thread {
        private Socket socket;
        private ChatServer server;
        private DataInputStream in;
        private DataOutputStream out;
        private String userName;

        ClientHandler(Socket socket, ChatServer server) {
            this.socket = socket;
            this.server = server;
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                // 첫 번째로 클라이언트가 보내는 건 사용자 이름
                userName = in.readUTF();
                System.out.println(userName + " connected from " + socket.getRemoteSocketAddress());

                // [추가] ★ 입장 알림 방송 ★
                server.broadcastSystem("[알림] " + userName + "님이 입장하셨습니다.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                while (true) {
                    String type = in.readUTF(); // "TEXT" 또는 "IMAGE"
                    if ("TEXT".equals(type)) {
                        String msg = in.readUTF();
                        server.broadcastText(userName, msg);
                    } else if ("IMAGE".equals(type)) {
                        String fileName = in.readUTF();
                        int len = in.readInt();
                        byte[] data = new byte[len];
                        in.readFully(data);
                        server.broadcastImage(userName, fileName, data);
                    }else if ("WHISPER".equals(type)) {
                        String targetName = in.readUTF(); // 받는 사람 이름
                        String msg = in.readUTF();        // 암호화된 메시지
                        server.sendWhisper(userName, targetName, msg);
                    }
                }
            } catch (IOException e) {
                System.out.println(userName + " disconnected.");
            } finally {
                server.removeClient(this);

                // [추가] ★ 퇴장 알림 방송 ★
                // userName이 null이 아닐 때만 방송 (연결하자마자 끊긴 경우 방지)
                if (userName != null) {
                    server.broadcastSystem("[알림] " + userName + "님이 퇴장하셨습니다.");
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        synchronized void sendText(String sender, String message) {
            try {
                out.writeUTF("TEXT");
                out.writeUTF(sender);
                out.writeUTF(message);
                out.flush();
            } catch (IOException e) {
                // 클라 죽었으면 그냥 무시
            }
        }

        synchronized void sendImage(String sender, String fileName, byte[] data) {
            try {
                out.writeUTF("IMAGE");
                out.writeUTF(sender);
                out.writeUTF(fileName);
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            } catch (IOException e) {
                // 마찬가지로 무시
            }
        }

        /** [추가] 귓속말 패킷 전송 **/
        synchronized void sendWhisperPacket(String sender, String message) {
            try {
                out.writeUTF("WHISPER"); // 클라이언트가 구분할 헤더
                out.writeUTF(sender);    // 보낸 사람
                out.writeUTF(message);   // 내용
                out.flush();
            } catch (IOException e) {
                // 무시
            }
        }
        // [추가] 시스템 메시지 전송용 (암호화 X)
        synchronized void sendSystemMessage(String message) {
            try {
                out.writeUTF("SYSTEM"); // 헤더
                out.writeUTF(message);  // 내용
                out.flush();
            } catch (IOException e) {
                // 무시
            }
        }

        // [추가] 이름을 비교하기 위해 필요
        public String getUserName() {
            return userName;
        }
    }
}