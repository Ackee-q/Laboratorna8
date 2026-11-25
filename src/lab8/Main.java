package lab8;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Головний клас лабораторної роботи №8 (Рівень ІІ).
 * Тема: «Обмін даними через мережу Internet» (TCP Sockets + Java Swing).
 *
 * Режими запуску:
 *   java lab8.Main          -> запускає одночасно вікна Сервера та Клієнта поруч
 *   java lab8.Main --server -> запускає тільки Серверний додаток (на пристрої №1)
 *   java lab8.Main --client -> запускає тільки Клієнтський додаток (на пристрої №2)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equalsIgnoreCase("--server")) {
            SwingUtilities.invokeLater(() -> {
                ServerApp server = new ServerApp();
                server.setVisible(true);
            });
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("--client")) {
            SwingUtilities.invokeLater(() -> {
                ClientApp client = new ClientApp();
                client.setVisible(true);
            });
            return;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("--screenshot")) {
            runAutomatedDemoAndSaveScreenshot(new File(args[1]));
            return;
        }

        // Режим за замовчуванням: відкриває форми Сервера і Клієнта поруч та встановлює зв'язок
        final ServerApp[] serverHolder = new ServerApp[1];
        final ClientApp[] clientHolder = new ClientApp[1];

        SwingUtilities.invokeAndWait(() -> {
            serverHolder[0] = new ServerApp();
            clientHolder[0] = new ClientApp();
            serverHolder[0].setVisible(true);
            clientHolder[0].setVisible(true);
            serverHolder[0].startServer();
        });

        Thread.sleep(250);
        SwingUtilities.invokeLater(() -> clientHolder[0].connectToServer());
    }

    /**
     * Виконує реальний двосторонній обмін повідомленнями через TCP-сокети
     * та зберігає комбінований скріншот обох вікон для звіту.
     */
    private static void runAutomatedDemoAndSaveScreenshot(File outFile) throws Exception {
        final ServerApp[] serverRef = new ServerApp[1];
        final ClientApp[] clientRef = new ClientApp[1];

        SwingUtilities.invokeAndWait(() -> {
            serverRef[0] = new ServerApp();
            clientRef[0] = new ClientApp();
            serverRef[0].setVisible(true);
            clientRef[0].setVisible(true);
            serverRef[0].startServer();
        });

        Thread.sleep(250);
        SwingUtilities.invokeAndWait(() -> clientRef[0].connectToServer());

        for (int i = 0; i < 30 && !clientRef[0].isConnected(); i++) {
            Thread.sleep(100);
        }
        Thread.sleep(200);

        clientRef[0].sendMessage("Привіт, сервере! Перевірка зв'язку по TCP/IP.");
        Thread.sleep(180);

        serverRef[0].sendMessage("Вітаю! Повідомлення отримано успішно.");
        Thread.sleep(180);

        clientRef[0].sendMessage("Двосторонній обмін між пристроями працює!");
        Thread.sleep(180);

        serverRef[0].sendMessage("Підтверджую! Канал Socket активний в обидві сторони.");
        Thread.sleep(200);

        clientRef[0].setDraftText("Дякую за відповідь!");
        serverRef[0].setDraftText("Готовий до роботи!");
        Thread.sleep(250);

        SwingUtilities.invokeAndWait(() -> {
            try {
                ServerApp s = serverRef[0];
                ClientApp c = clientRef[0];
                int gap = 20;
                int pad = 16;
                int headerH = 34;

                int w1 = s.getContentPane().getWidth();
                int h1 = s.getContentPane().getHeight();
                int w2 = c.getContentPane().getWidth();
                int h2 = c.getContentPane().getHeight();

                int cardW1 = w1 + 4;
                int cardH1 = h1 + headerH + 4;
                int cardW2 = w2 + 4;
                int cardH2 = h2 + headerH + 4;

                int totalW = pad * 2 + cardW1 + gap + cardW2;
                int totalH = pad * 2 + Math.max(cardH1, cardH2);

                BufferedImage combined = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = combined.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

                g2.setColor(new Color(242, 244, 248));
                g2.fillRect(0, 0, totalW, totalH);

                drawWindowCard(g2, s.getTitle(), s.getContentPane(), pad, pad, cardW1, cardH1, headerH);
                drawWindowCard(g2, c.getTitle(), c.getContentPane(), pad + cardW1 + gap, pad, cardW2, cardH2, headerH);

                g2.dispose();
                ImageIO.write(combined, "png", outFile);
                System.out.println("Збережено скріншот: " + outFile.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        clientRef[0].disconnect();
        serverRef[0].shutdown();
        SwingUtilities.invokeAndWait(() -> {
            clientRef[0].dispose();
            serverRef[0].dispose();
        });
        System.exit(0);
    }

    private static void drawWindowCard(Graphics2D g2, String title, java.awt.Container content,
                                       int x, int y, int cardW, int cardH, int headerH) {
        g2.setColor(new Color(180, 186, 196));
        g2.fillRect(x, y, cardW, cardH);

        g2.setColor(new Color(250, 250, 252));
        g2.fillRect(x + 2, y + 2, cardW - 4, headerH - 2);

        g2.setColor(new Color(215, 220, 228));
        g2.drawLine(x + 2, y + headerH, x + cardW - 3, y + headerH);

        g2.setColor(new Color(30, 35, 45));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.drawString(title, x + 12, y + 22);

        // Іконки керування вікном Windows справа зверху
        int bx = x + cardW - 95;
        g2.setColor(new Color(90, 95, 105));
        g2.drawLine(bx, y + 18, bx + 10, y + 18);
        g2.drawRect(bx + 32, y + 13, 10, 10);
        g2.drawLine(bx + 64, y + 13, bx + 74, y + 23);
        g2.drawLine(bx + 74, y + 13, bx + 64, y + 23);

        Graphics2D sub = (Graphics2D) g2.create(x + 2, y + headerH + 2, content.getWidth(), content.getHeight());
        content.printAll(sub);
        sub.dispose();
    }
}
