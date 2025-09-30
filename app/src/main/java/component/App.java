package component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App().show());
    }

    enum Screen { MENU, SETTINGS, SCOREBOARD }

    private final JFrame frame = new JFrame("TETRIS");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final MenuPanel menuPanel = new MenuPanel(this::onMenuSelect);
    private final JPanel settingsPanel = stubPanel("설정 (Settings) – 추후 구현");
    private final JPanel scoreboardPanel = stubPanel("스코어보드 (Scoreboard) – 추후 구현");

    private void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(480, 560);
        frame.setLocationRelativeTo(null);

        root.add(menuPanel, Screen.MENU.name());
        root.add(settingsPanel, Screen.SETTINGS.name());
        root.add(scoreboardPanel, Screen.SCOREBOARD.name());

        frame.setContentPane(root);
        frame.setVisible(true);

        showScreen(Screen.MENU);
    }

    private void showScreen(Screen s) {
        cards.show(root, s.name());
        root.requestFocusInWindow();
    }


    private JPanel stubPanel(String text) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 18f));
        p.add(l, BorderLayout.CENTER);

    
        InputMap im = p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = p.getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        am.put("back", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                showScreen(Screen.MENU);
            }
        });
        return p;
    }

    private void onMenuSelect(MenuPanel.MenuItem item) {
        switch (item) {
            case START -> launchBoard();
            case SETTINGS -> showScreen(Screen.SETTINGS);
            case SCOREBOARD -> showScreen(Screen.SCOREBOARD);
            case EXIT -> System.exit(0);
        }
    }

    private void launchBoard() {

        frame.setVisible(false);

        Board game = new Board();
        game.setTitle("TETRIS – Game");
        game.setLocationRelativeTo(null);
        game.setVisible(true);

        
        SwingUtilities.invokeLater(() -> {
            game.requestFocusInWindow();
            game.requestFocus();
            game.toFront();
        });

        game.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                frame.setVisible(true);
                showScreen(Screen.MENU);
            }
            @Override public void windowClosing(WindowEvent e) {
                game.dispose();
            }
        });
    }
}
