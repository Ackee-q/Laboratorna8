package lab8;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

/**
 * Клієнтський графічний додаток для лабораторної роботи №8 (Рівень ІІ).
 * Дозволяє вказати IP-адресу віддаленого пристрою та порт сервера і здійснювати
 * двосторонній обмін текстовими повідомленнями через TCP-сокет.
 */
public class ClientApp extends JFrame {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextField inputHost = new JTextField("127.0.0.1", 10);
    private final JTextField inputPort = new JTextField("6666", 5);
    private final JButton buttonConnect = new JButton("Підключитися");
    private final JLabel labelStatus = new JLabel(" Статус: не підключено");

    private final JTextArea textAreaMessages = new JTextArea(12, 34);
    private final JTextField inputMessage = new JTextField("", 22);
    private final JButton buttonSend = new JButton("Відправити серверу");

    private volatile Socket socket;
    private volatile DataInputStream in;
    private volatile DataOutputStream out;
    private volatile boolean connected = false;

    public ClientApp() {
        super("Лабораторна №8 (2-й рівень) - Клієнт");
        this.setBounds(640, 180, 500, 420);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Container container = this.getContentPane();
        container.setLayout(new BorderLayout(8, 8));

        // Верхня панель введення IP-адреси та порту віддаленого сервера
        JPanel topWrapper = new JPanel(new GridLayout(2, 1, 4, 4));
        topWrapper.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));

        JPanel connPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        connPanel.add(new JLabel("IP сервера:"));
        connPanel.add(inputHost);
        connPanel.add(new JLabel("Порт:"));
        connPanel.add(inputPort);
        connPanel.add(buttonConnect);

        labelStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        topWrapper.add(connPanel);
        topWrapper.add(labelStatus);
        container.add(topWrapper, BorderLayout.NORTH);

        // Центральна область історії повідомлень клієнта
        textAreaMessages.setEditable(false);
        textAreaMessages.setLineWrap(true);
        textAreaMessages.setWrapStyleWord(true);
        textAreaMessages.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(textAreaMessages);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Діалог повідомлень (Клієнтська форма):"));
        container.add(scrollPane, BorderLayout.CENTER);

        // Нижня панель введення тексту та кнопки відправлення серверу
        JPanel bottomPanel = new JPanel(new BorderLayout(6, 6));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        bottomPanel.add(new JLabel("Повідомлення:"), BorderLayout.WEST);
        bottomPanel.add(inputMessage, BorderLayout.CENTER);
        bottomPanel.add(buttonSend, BorderLayout.EAST);
        buttonSend.setEnabled(false);
        container.add(bottomPanel, BorderLayout.SOUTH);

        buttonConnect.addActionListener(e -> connectToServer());
        buttonSend.addActionListener(e -> sendFromInput());
        inputMessage.addActionListener(e -> sendFromInput());
    }

    public void connectToServer() {
        if (connected) {
            return;
        }
        String host = inputHost.getText().trim();
        int port;
        try {
            port = Integer.parseInt(inputPort.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Некоректний номер порту!", "Помилка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Thread connectThread = new Thread(() -> {
            try {
                InetAddress ipAddress = InetAddress.getByName(host);
                socket = new Socket(ipAddress, port);
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                connected = true;

                SwingUtilities.invokeLater(() -> {
                    buttonConnect.setEnabled(false);
                    inputHost.setEnabled(false);
                    inputPort.setEnabled(false);
                    buttonSend.setEnabled(true);
                    labelStatus.setText(" Статус: підключено до " + host + ":" + port);
                });
                appendLog("[Система] Встановлено TCP-з'єднання із сервером " + host + ":" + port);

                listenFromServer();
            } catch (IOException ex) {
                appendLog("[Помилка підключення] " + ex.getMessage());
            }
        }, "Client-Receive-Thread");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void listenFromServer() {
        try {
            while (connected && socket != null && !socket.isClosed()) {
                String serverReply = in.readUTF();
                appendLog("[" + LocalTime.now().format(TIME_FMT) + "] Сервер: " + serverReply);
            }
        } catch (EOFException eof) {
            appendLog("[Система] Сервер завершив з'єднання.");
        } catch (IOException ex) {
            if (connected) {
                appendLog("[Система] Зв'язок із сервером перервано.");
            }
        } finally {
            connected = false;
            SwingUtilities.invokeLater(() -> {
                buttonSend.setEnabled(false);
                buttonConnect.setEnabled(true);
                inputHost.setEnabled(true);
                inputPort.setEnabled(true);
                labelStatus.setText(" Статус: від'єднано");
            });
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
        if (!connected || out == null) {
            appendLog("[Попередження] Спочатку підключіться до сервера.");
            return;
        }
        try {
            out.writeUTF(text);
            out.flush();
            appendLog("[" + LocalTime.now().format(TIME_FMT) + "] Клієнт (Ви): " + text);
        } catch (IOException ex) {
            appendLog("[Помилка відправки] " + ex.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void setDraftText(String draft) {
        SwingUtilities.invokeLater(() -> inputMessage.setText(draft));
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    private void appendLog(String line) {
        System.out.println("[CLIENT] " + line);
        SwingUtilities.invokeLater(() -> {
            textAreaMessages.append(line + "\n");
            textAreaMessages.setCaretPosition(textAreaMessages.getDocument().getLength());
        });
    }
}
