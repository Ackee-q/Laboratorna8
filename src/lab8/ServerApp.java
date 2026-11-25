package lab8;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

/**
 * Серверний графічний додаток для лабораторної роботи №8 (Рівень ІІ).
 * Підтримує підключення клієнта як на одному пристрої (127.0.0.1), так і з іншого
 * пристрою в мережі TCP/IP, а також двосторонній обмін текстовими повідомленнями.
 */
public class ServerApp extends JFrame {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextField inputPort = new JTextField("6666", 6);
    private final JButton buttonStart = new JButton("Запустити сервер");
    private final JLabel labelStatus = new JLabel(" Статус: сервер не запущено");

    private final JTextArea textAreaMessages = new JTextArea(12, 34);
    private final JTextField inputMessage = new JTextField("", 22);
    private final JButton buttonSend = new JButton("Відправити клієнту");

    private volatile ServerSocket serverSocket;
    private volatile Socket clientSocket;
    private volatile DataInputStream in;
    private volatile DataOutputStream out;
    private volatile boolean running = false;

    public ServerApp() {
        super("Лабораторна №8 (2-й рівень) - Сервер");
        this.setBounds(120, 180, 500, 420);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Container container = this.getContentPane();
        container.setLayout(new BorderLayout(8, 8));

        // Верхня панель керування сокетом сервера
        JPanel topWrapper = new JPanel(new GridLayout(2, 1, 4, 4));
        topWrapper.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));

        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        String localIp = getLocalHostIp();
        portPanel.add(new JLabel("IP сервера: " + localIp + "  |  Порт:"));
        portPanel.add(inputPort);
        portPanel.add(buttonStart);

        labelStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        topWrapper.add(portPanel);
        topWrapper.add(labelStatus);
        container.add(topWrapper, BorderLayout.NORTH);

        // Центральна область відображення отриманих та відправлених повідомлень
        textAreaMessages.setEditable(false);
        textAreaMessages.setLineWrap(true);
        textAreaMessages.setWrapStyleWord(true);
        textAreaMessages.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(textAreaMessages);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Діалог повідомлень (Серверна форма):"));
        container.add(scrollPane, BorderLayout.CENTER);

        // Нижня панель введення та відправлення відповіді клієнту (Рівень ІІ)
        JPanel bottomPanel = new JPanel(new BorderLayout(6, 6));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        bottomPanel.add(new JLabel("Повідомлення:"), BorderLayout.WEST);
        bottomPanel.add(inputMessage, BorderLayout.CENTER);
        bottomPanel.add(buttonSend, BorderLayout.EAST);
        buttonSend.setEnabled(false);
        container.add(bottomPanel, BorderLayout.SOUTH);

        buttonStart.addActionListener(e -> startServer());
        buttonSend.addActionListener(e -> sendFromInput());
        inputMessage.addActionListener(e -> sendFromInput());
    }

    public void startServer() {
        if (running) {
            return;
        }
        int port;
        try {
            port = Integer.parseInt(inputPort.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Некоректний номер порту!", "Помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // Прив'язка до 0.0.0.0 дозволяє приймати з'єднання з інших пристроїв у мережі
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            running = true;
            buttonStart.setEnabled(false);
            inputPort.setEnabled(false);
            updateStatus(" Статус: очікування клієнта на порту " + port + "...");
            appendLog("[Система] Сервер запущено на порту " + port + ". Очікування підключення...");

            Thread acceptThread = new Thread(this::listenLoop, "Server-Accept-Thread");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException ex) {
            appendLog("[Помилка] Не вдалося запустити сервер: " + ex.getMessage());
        }
    }

    private void listenLoop() {
        while (running) {
            try {
                Socket accepted = serverSocket.accept();
                this.clientSocket = accepted;
                this.in = new DataInputStream(accepted.getInputStream());
                this.out = new DataOutputStream(accepted.getOutputStream());

                String remoteAddr = accepted.getInetAddress().getHostAddress() + ":" + accepted.getPort();
                SwingUtilities.invokeLater(() -> {
                    labelStatus.setText(" Статус: підключено клієнта (" + remoteAddr + ")");
                    buttonSend.setEnabled(true);
                });
                appendLog("[Система] Клієнт підключився з адреси " + remoteAddr);

                while (running && !accepted.isClosed()) {
                    String message = in.readUTF();
                    appendLog("[" + LocalTime.now().format(TIME_FMT) + "] Клієнт: " + message);
                }
            } catch (EOFException eof) {
                appendLog("[Система] Клієнт від'єднався.");
                SwingUtilities.invokeLater(() -> {
                    buttonSend.setEnabled(false);
                    labelStatus.setText(" Статус: очікування нового клієнта...");
                });
            } catch (IOException ex) {
                if (running) {
                    appendLog("[Система] З'єднання закрито: " + ex.getMessage());
                }
            }
        }
    }

    private void sendFromInput() {
        String text = inputMessage.getText().trim();
        if (!text.isEmpty()) {
            sendMessage(text);
            inputMessage.setText("");
            inputMessage.requestFocus();
        }
    }

    public synchronized void sendMessage(String text) {
        if (out == null) {
            appendLog("[Попередження] Немає активного підключення клієнта.");
            return;
        }
        try {
            out.writeUTF(text);
            out.flush();
            appendLog("[" + LocalTime.now().format(TIME_FMT) + "] Сервер (Ви): " + text);
        } catch (IOException ex) {
            appendLog("[Помилка відправки] " + ex.getMessage());
        }
    }

    public void setDraftText(String draft) {
        SwingUtilities.invokeLater(() -> inputMessage.setText(draft));
    }

    public void shutdown() {
        running = false;
        try {
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ignored) {
        }
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    private void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> labelStatus.setText(status));
    }

    private void appendLog(String line) {
        System.out.println("[SERVER] " + line);
        SwingUtilities.invokeLater(() -> {
            textAreaMessages.append(line + "\n");
            textAreaMessages.setCaretPosition(textAreaMessages.getDocument().getLength());
        });
    }

    private String getLocalHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
