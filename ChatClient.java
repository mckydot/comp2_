import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClient extends JFrame {

    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton imageButton;
    private JButton loadLogButton;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private String userName;
    private File logFile = new File("chatlog.txt");           // 복호화된 일반 로그
    private File encLogFile = new File("chatlog_enc.txt");    // 암호화된 로그

    // 암호키 (간단 XOR)
    private final String xorKey = "secret1234";

    public ChatClient() {

        // OS 기본 룩앤필 적용 (외부 라이브러리 불필요)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setTitle("간단 채팅 클라이언트 (암호화 + 사용자구분)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);

        initGui();
        connectToServer();
        startReceiverThread();
    }

    private void initGui() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 채팅창: JTextPane + StyledDocument 사용
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        chatArea.setBackground(Color.WHITE);
        chatArea.setMargin(new Insets(10, 8, 10, 8));
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("채팅"));

        // 입력부
        inputField = new JTextField();
        inputField.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        inputField.setPreferredSize(new Dimension(0, 38));

        sendButton = new JButton("전송");
        imageButton = new JButton("이미지 보내기");
        loadLogButton = new JButton("로그 불러오기");

        Font btnFont = new Font("맑은 고딕", Font.BOLD, 13);
        sendButton.setFont(btnFont);
        imageButton.setFont(btnFont);
        loadLogButton.setFont(btnFont);

        sendButton.setFocusPainted(false);
        imageButton.setFocusPainted(false);
        loadLogButton.setFocusPainted(false);

        // 버튼 스타일(배경색) — 플랫폼에 따라 무시될 수 있음
        sendButton.setBackground(new Color(60, 140, 230));
        sendButton.setForeground(Color.WHITE);

        imageButton.setBackground(new Color(90, 90, 90));
        imageButton.setForeground(Color.WHITE);

        loadLogButton.setBackground(new Color(100, 180, 60));
        loadLogButton.setForeground(Color.WHITE);

        sendButton.setForeground(Color.BLACK);
        imageButton.setForeground(Color.BLACK);
        loadLogButton.setForeground(Color.BLACK);


        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.add(imageButton);
        rightPanel.add(loadLogButton);
        rightPanel.add(sendButton);

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // 액션 연결
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
            userName = userName.trim();

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

            // 첫 번째로 이름 전송 (서버와 프로토콜 일치)
            out.writeUTF(userName);
            out.flush();

            appendSystemMessage("[시스템] 서버에 연결되었습니다.");
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
                        String encryptedMsg = in.readUTF();

                        // 암호화된 로그 별도 저장
                        appendEncLog(sender + ": " + toHexString(encryptedMsg.getBytes(StandardCharsets.ISO_8859_1)));

                        // 복호화
                        String decrypted = xorMessage(encryptedMsg);

                        // 구분해서 화면에 출력
                        if (sender.equals(userName)) {
                            appendMyMessage(decrypted);
                        } else {
                            appendOtherMessage(sender + ": " + decrypted);
                        }

                        // 일반(복호화된) 로그 저장
                        appendLog(sender + ": " + decrypted);

                    } else if ("IMAGE".equals(type)) {
                        String sender = in.readUTF();
                        String fileName = in.readUTF();
                        int len = in.readInt();
                        byte[] data = new byte[len];
                        in.readFully(data);

                        File dir = new File("downloads");
                        if (!dir.exists()) dir.mkdirs();
                        File outputFile = new File(dir, System.currentTimeMillis() + "_" + fileName);
                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            fos.write(data);
                        }

                        String line = sender + "님이 이미지를 보냈습니다: " + outputFile.getAbsolutePath();
                        appendOtherMessage(line);     // 이미지 알림은 왼쪽(타인) 스타일로
                        appendLog(line);
                        // 미리보기
                        SwingUtilities.invokeLater(() -> {
                            ImageIcon icon = new ImageIcon(data);
                            JLabel imgLabel = new JLabel(icon);
                            JScrollPane sp = new JScrollPane(imgLabel);
                            sp.setPreferredSize(new Dimension(420, 320));
                            JOptionPane.showMessageDialog(this, sp, "이미지 from " + sender, JOptionPane.PLAIN_MESSAGE);
                        });
                    }// [추가] 귓속말 수신 처리
                    else if ("WHISPER".equals(type)) {
                        String sender = in.readUTF();
                        String encryptedMsg = in.readUTF();

                        // 귓속말은 로그에 [귓] 표시해서 저장 (선택)
                        appendEncLog("[귓]" + sender + ": " + toHexString(encryptedMsg.getBytes(StandardCharsets.ISO_8859_1)));

                        String decrypted = xorMessage(encryptedMsg);

                        // 화면에 보라색으로 출력
                        appendWhisperMessage(sender + "님의 귓속말", decrypted);
                        appendLog("[귓]" + sender + ": " + decrypted);
                    }// [추가] ★ 시스템 메시지 처리 ★
                    else if ("SYSTEM".equals(type)) {
                        String msg = in.readUTF(); // 암호화 안 된 평문 읽기
                        appendSystemMessage(msg);  // 화면 중앙에 회색으로 출력
                        appendLog(msg);            // 로그에도 저장 (선택사항)
                    }
                }
            } catch (IOException e) {
                appendSystemMessage("[시스템] 서버와의 연결이 끊어졌습니다.");
            } finally {
                try {
                    if (socket != null) socket.close();
                } catch (IOException ignored) {}
            }
        });

        receiver.setDaemon(true);
        receiver.start();
    }

    // 텍스트 전송 (암호화 후 전송)
    // 텍스트 전송 (귓속말 기능 추가)
    private void sendTextMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        try {
            // 1. 귓속말인지 확인 ("/귓 닉네임 할말" 형식)
            if (text.startsWith("/귓 ")) {
                String[] parts = text.split(" ", 3); // 공백 기준 3덩어리로 나눔

                if (parts.length == 3) {
                    String targetName = parts[1];
                    String content = parts[2];

                    out.writeUTF("WHISPER");           // 1. 타입
                    out.writeUTF(targetName);          // 2. 받는 사람
                    out.writeUTF(xorMessage(content)); // 3. 암호화된 내용
                    out.flush();

                    // 내 화면에도 표시 (보라색으로)
                    appendWhisperMessage("나 -> " + targetName, content);
                } else {
                    appendSystemMessage("[시스템] 사용법: /귓 [상대방이름] [할말]");
                    return; // 전송 안 하고 종료
                }
            }
            // 2. 일반 메시지 (기존 로직)
            else {
                out.writeUTF("TEXT");
                out.writeUTF(xorMessage(text));
                out.flush();
            }

            inputField.setText("");

        } catch (IOException e) {
            appendSystemMessage("[에러] 메시지를 보낼 수 없습니다: " + e.getMessage());
        }
    }

    // 이미지 전송 (암호화 없이 원래대로)
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

            appendSystemMessage("[시스템] 이미지를 전송했습니다: " + file.getName());

        } catch (IOException e) {
            appendSystemMessage("[에러] 이미지를 보낼 수 없습니다: " + e.getMessage());
        }
    }

    // 파일 읽기 유틸
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

    // XOR 암호화/복호화 (같은 함수가 암/복호 모두 담당)
    private String xorMessage(String message) {
        // Java에서 char는 16-bit이므로, 암호화 결과를 안전히 전송하려면
        // 읽고 쓸 때 동일한 방식으로 처리해야 함.
        // 여기서는 char 단위 XOR 후 그 결과를 문자열로 바로 사용함.
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char c = (char) (message.charAt(i) ^ xorKey.charAt(i % xorKey.length()));
            result.append(c);
        }
        return result.toString();
    }

    // ---- UI: Styled append helpers ----

    // 시스템 메시지 (중앙 정렬, 회색)
    private void appendSystemMessage(String msg) {
        SwingUtilities.invokeLater(() -> appendStyledMessage(msg + "\n",
                new Color(120, 120, 120), new Color(245, 245, 245), StyleConstants.ALIGN_CENTER));
    }

    // 내가 보낸 메시지: 오른쪽 정렬, 파란 텍스트/연한 파랑 배경
    private void appendMyMessage(String msg) {
        SwingUtilities.invokeLater(() -> appendStyledMessage("[나] " + msg + "\n",
                new Color(20, 50, 120), new Color(220, 235, 255), StyleConstants.ALIGN_RIGHT));
    }

    // 다른 사람 메시지: 왼쪽 정렬, 검정 텍스트/연한 회색 배경
    private void appendOtherMessage(String msg) {
        SwingUtilities.invokeLater(() -> appendStyledMessage(msg + "\n",
                Color.BLACK, new Color(245, 245, 245), StyleConstants.ALIGN_LEFT));
    }

    // 핵심: StyledDocument에 삽입하고 단락 정렬/배경 등 설정
    private void appendStyledMessage(String text, Color fg, Color bg, int alignment) {
        StyledDocument doc = chatArea.getStyledDocument();
        SimpleAttributeSet attrs = new SimpleAttributeSet();

        StyleConstants.setForeground(attrs, fg);
        StyleConstants.setBackground(attrs, bg);
        StyleConstants.setFontSize(attrs, 14);
        StyleConstants.setFontFamily(attrs, "맑은 고딕");
        StyleConstants.setAlignment(attrs, alignment);
        StyleConstants.setLeftIndent(attrs, 6);
        StyleConstants.setRightIndent(attrs, 6);
        StyleConstants.setSpaceAbove(attrs, 4);
        StyleConstants.setSpaceBelow(attrs, 4);

        try {
            int start = doc.getLength();
            doc.insertString(start, text, attrs);

            // paragraph attributes 적용: 시작위치, 길이
            int len = text.length();
            doc.setParagraphAttributes(start, len, attrs, false);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        // 스크롤 최하단으로
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // [추가] 귓속말 UI: 왼쪽 정렬, 보라색 텍스트/연한 보라 배경
    private void appendWhisperMessage(String title, String msg) {
        SwingUtilities.invokeLater(() -> appendStyledMessage("[" + title + "] " + msg + "\n",
                new Color(180, 0, 180), new Color(250, 230, 250), StyleConstants.ALIGN_LEFT));
    }

    // ---- 로그 저장 ----
    private void appendLog(String line) {
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter pw = new PrintWriter(bw)) {
            pw.println(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 암호화된 로그(16진 표기) 저장
    private void appendEncLog(String line) {
        try (FileWriter fw = new FileWriter(encLogFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter pw = new PrintWriter(bw)) {
            pw.println(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 로그 읽기(복호화된 일반 로그)
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
        logArea.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(600, 380));
        JOptionPane.showMessageDialog(this, scrollPane, "채팅 로그", JOptionPane.PLAIN_MESSAGE);
    }

    // --- 유틸: 문자열 바이트를 16진 문자열로 변환(암호화 로그 표기용) ---
    private String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient();
            client.setVisible(true);
        });
    }
}