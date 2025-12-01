import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;

public class ChatClient extends JFrame {

    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton imageButton;
    private JButton loadLogButton;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private String userName;
    private File logFile = new File("chatlog.txt");// 일반 대화 로그
    private File encLogFile = new File("chatlog_enc.txt"); // 암호화된 외계어 로그
    public ChatClient() {
        setTitle("간단 채팅 클라이언트");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        initGui();
        connectToServer();
        startReceiverThread();
    }

    private void initGui() {
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        inputField = new JTextField();
        sendButton = new JButton("전송");
        imageButton = new JButton("이미지 보내기");
        loadLogButton = new JButton("로그 불러오기");

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        bottomPanel.add(inputField, BorderLayout.CENTER);
        rightPanel.add(sendButton);
        rightPanel.add(imageButton);
        rightPanel.add(loadLogButton);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // 엔터키로 전송
        inputField.addActionListener(e -> sendTextMessage());
        sendButton.addActionListener(e -> sendTextMessage());
        imageButton.addActionListener(e -> sendImage());
        loadLogButton.addActionListener(e -> loadLog());
    }

    private void connectToServer() {
        try {
            userName = JOptionPane.showInputDialog(this, "사용할 닉네임을 입력하세요:", "닉네임", JOptionPane.QUESTION_MESSAGE);
            if (userName == null || userName.trim().isEmpty()) {
                System.exit(0);
            }

            String host = JOptionPane.showInputDialog(this, "서버 IP를 입력하세요 (예: 127.0.0.1):", "127.0.0.1");
            if (host == null || host.trim().isEmpty()) {
                host = "127.0.0.1";
            }

            String portStr = JOptionPane.showInputDialog(this, "포트를 입력하세요:", "5000");
            int port = 5000;
            if (portStr != null && !portStr.trim().isEmpty()) {
                port = Integer.parseInt(portStr.trim());
            }

            socket = new Socket(host, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // 첫 번째로 이름 전송
            out.writeUTF(userName);
            out.flush();

            appendMessage("[시스템] 서버에 연결되었습니다.");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버에 연결할 수 없습니다: " + e.getMessage());
            System.exit(0);
        }
    }

    private void startReceiverThread() {
        Thread receiver = new Thread(() -> {
            try {
                while (true) {
                    String type = in.readUTF();
                    if ("TEXT".equals(type)) {
                        String sender = in.readUTF();
                        String encryptedMsg = in.readUTF(); // 일단 암호화된 걸 받음
                        // [추가] 암호화된 상태 그대로 별도 파일에 저장!
                        appendEncLog(sender + ": " + encryptedMsg);

                        // 2. 복호화 (암호 풀기)
                        String decryptedMsg = xorMessage(encryptedMsg);

                        // 3. 화면에 띄우고 일반 로그에 저장
                        String line = sender + ": " + decryptedMsg;
                        appendMessage(line);
                        appendLog(line); // 이건 해석된 말 저장
                    } else if ("IMAGE".equals(type)) {
                        String sender = in.readUTF();
                        String fileName = in.readUTF();
                        int len = in.readInt();
                        byte[] data = new byte[len];
                        in.readFully(data);

                        File dir = new File("downloads");
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        File outputFile = new File(dir, System.currentTimeMillis() + "_" + fileName);
                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            fos.write(data);
                        }

                        String line = sender + "님이 이미지를 보냈습니다: " + outputFile.getAbsolutePath();
                        appendMessage(line);
                        appendLog(line);

                        // 이미지 미리보기 (선택적이지만 과제에 어필하기 좋음)
                        ImageIcon icon = new ImageIcon(data);
                        JLabel imgLabel = new JLabel(icon);
                        JScrollPane sp = new JScrollPane(imgLabel);
                        sp.setPreferredSize(new Dimension(400, 300));
                        JOptionPane.showMessageDialog(this, sp,
                                "이미지 from " + sender,
                                JOptionPane.PLAIN_MESSAGE);
                    }
                }
            } catch (IOException e) {
                appendMessage("[시스템] 서버와의 연결이 끊어졌습니다.");
            } finally {
                try {
                    if (socket != null) socket.close();
                } catch (IOException ignored) {}
            }
        });

        receiver.setDaemon(true);
        receiver.start();
    }

    private void sendTextMessage() {  //약간 변경된 메소드
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        try {
            out.writeUTF("TEXT");
            // [수정] 입력한 텍스트를 암호화해서 전송!
            String encryptedText = xorMessage(text);
            out.writeUTF(encryptedText);
            //수정 완
            out.flush();
            inputField.setText("");
            // 서버가 브로드캐스트해서 다시 보내줄 거라 여기서 따로 추가 안 해도 됨
        } catch (IOException e) {
            appendMessage("[에러] 메시지를 보낼 수 없습니다: " + e.getMessage());
        }
    }
    // 이 메소드를 ChatClient 클래스 안에 추가해줘
    private String xorMessage(String message) {
        String key = "secret1234"; // 암호화 키 (친구랑 똑같이 맞춰야 함)
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < message.length(); i++) {
            // 문자를 XOR 연산해서 변환 (암호화 <-> 복호화 동일 로직)
            result.append((char)(message.charAt(i) ^ key.charAt(i % key.length())));
        }

        return result.toString();
    }

    private void sendImage() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        try {
            byte[] data = readFileToByteArray(file);

            out.writeUTF("IMAGE");
            out.writeUTF(file.getName());
            out.writeInt(data.length);
            out.write(data);
            out.flush();

            appendMessage("[시스템] 이미지를 전송했습니다: " + file.getName());

        } catch (IOException e) {
            appendMessage("[에러] 이미지를 보낼 수 없습니다: " + e.getMessage());
        }
    }

    private byte[] readFileToByteArray(File file) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(file)) {

            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        }
    }

    private void appendMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(msg + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    // 파일 쓰기 (로그 저장)
    private void appendLog(String line) {
        try (FileWriter fw = new FileWriter(logFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw)) {
            pw.println(line);
        } catch (IOException e) {
            // 로그 저장 실패는 크게 중요하진 않으니 조용히 무시해도 됨
            e.printStackTrace();
        }
    }
    // 암호화된 로그만 따로 저장하는 메소드
    private void appendEncLog(String line) {
        try (FileWriter fw = new FileWriter(encLogFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter pw = new PrintWriter(bw)) {
            pw.println(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 파일 읽기 (로그 불러오기 기능)
    private void loadLog() {
        if (!logFile.exists()) {
            JOptionPane.showMessageDialog(this, "저장된 로그가 없습니다.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "로그를 읽는 중 오류: " + e.getMessage());
            return;
        }

        JTextArea logArea = new JTextArea(sb.toString());
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(500, 300));
        JOptionPane.showMessageDialog(this, scrollPane, "채팅 로그", JOptionPane.PLAIN_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient();
            client.setVisible(true);
        });
    }
}
