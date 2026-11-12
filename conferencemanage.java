import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

class ConferenceManagementSystem {
    private static final Path DATA_FILE = Path.of(
            System.getProperty("user.home"), "ConferenceManagementSystem-data.txt");

    private final List<Conference> conferences = new ArrayList<>();
    private final List<Conference> visibleConferences = new ArrayList<>();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> conferenceList = new JList<>(listModel);
    private final JTextArea detailsArea = new JTextArea();
    private final JFrame frame = new JFrame("Conference Management System");
    private boolean darkMode = false;
    private GradientPanel headerPanel;
    private JTextField searchField;
    private JLabel countLabel;
    private JLabel sectionTitle;
    private JLabel listHeading;
    private JLabel detailsHeading;

    private static class Conference {
        String name, date, venue, description, sessions;
        Conference(String name, String date, String venue, String description, String sessions) {
            this.name = name;
            this.date = date;
            this.venue = venue;
            this.description = description;
            this.sessions = sessions;
        }
        String displayName() { return name + "  |  " + date; }
        String details() {
            return "CONFERENCE DETAILS\n\n" +
                    "Name: " + name + "\n" +
                    "Date: " + date + "\n" +
                    "Venue: " + venue + "\n\n" +
                    "Description:\n" + description + "\n\n" +
                    "Sessions / Schedule:\n" + sessions;
        }
    }

    private void loadData() {
        if (!Files.isRegularFile(DATA_FILE)) return;
        try {
            for (String line : Files.readAllLines(DATA_FILE, StandardCharsets.UTF_8)) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 5) continue;
                try {
                    conferences.add(new Conference(decode(fields[0]), decode(fields[1]),
                            decode(fields[2]), decode(fields[3]), decode(fields[4])));
                } catch (IllegalArgumentException ignored) {
                    // Skip a damaged record and continue loading the remaining records.
                }
            }
        } catch (IOException ex) {
            showError("Could not load saved conferences: " + ex.getMessage());
        }
    }

    private void saveData() {
        List<String> lines = new ArrayList<>();
        for (Conference c : conferences) {
            lines.add(encode(c.name) + "\t" + encode(c.date) + "\t" + encode(c.venue)
                    + "\t" + encode(c.description) + "\t" + encode(c.sessions));
        }
        try {
            Files.write(DATA_FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            showError("Could not save conferences: " + ex.getMessage());
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void refreshList(int selectIndex) {
        listModel.clear();
        visibleConferences.clear();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        for (Conference c : conferences) {
            String searchable = (c.name + " " + c.date + " " + c.venue).toLowerCase();
            if (searchable.contains(query)) {
                visibleConferences.add(c);
                listModel.addElement(c.displayName());
            }
        }
        if (countLabel != null) {
            countLabel.setText(visibleConferences.size() + " of " + conferences.size() + " conferences");
        }
        if (selectIndex >= 0 && selectIndex < conferences.size()) {
            int visibleIndex = visibleConferences.indexOf(conferences.get(selectIndex));
            if (visibleIndex >= 0) {
                conferenceList.setSelectedIndex(visibleIndex);
                return;
            }
        }
        if (listModel.isEmpty()) {
            detailsArea.setText(query.isEmpty()
                    ? "No conferences yet. Add a conference to get started."
                    : "No conferences match your search.");
        } else {
            conferenceList.clearSelection();
            detailsArea.setText("Select a conference to view its details.");
        }
    }

    private int selectedIndex() {
        int visibleIndex = conferenceList.getSelectedIndex();
        if (visibleIndex < 0 || visibleIndex >= visibleConferences.size()) {
            JOptionPane.showMessageDialog(frame, "Please select a conference first.",
                    "No conference selected", JOptionPane.INFORMATION_MESSAGE);
            return -1;
        }
        return conferences.indexOf(visibleConferences.get(visibleIndex));
    }

    private Conference showConferenceForm(String title, Conference existing) {
        JTextField nameField = new JTextField(existing == null ? "" : existing.name, 24);
        JTextField dateField = new JTextField(existing == null ? "" : existing.date, 24);
        JTextField venueField = new JTextField(existing == null ? "" : existing.venue, 24);
        JTextArea descriptionField = new JTextArea(existing == null ? "" : existing.description, 3, 24);
        JTextArea sessionsField = new JTextArea(existing == null ? "" : existing.sessions, 4, 24);
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);
        sessionsField.setLineWrap(true);
        sessionsField.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        addFormRow(form, gbc, 0, "Conference name:", nameField);
        addFormRow(form, gbc, 1, "Date (e.g. 20 Dec 2026):", dateField);
        addFormRow(form, gbc, 2, "Venue:", venueField);
        addFormRow(form, gbc, 3, "Description:", new JScrollPane(descriptionField));
        addFormRow(form, gbc, 4, "Sessions / schedule:", new JScrollPane(sessionsField));

        int result = JOptionPane.showConfirmDialog(frame, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;

        String name = nameField.getText().trim();
        String date = dateField.getText().trim();
        String venue = venueField.getText().trim();
        if (name.isEmpty() || date.isEmpty() || venue.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "Conference name, date and venue are required.",
                    "Missing information", JOptionPane.WARNING_MESSAGE);
            return showConferenceForm(title, existing);
        }
        return new Conference(name, date, venue, descriptionField.getText().trim(),
                sessionsField.getText().trim());
    }

    private void addFormRow(JPanel form, GridBagConstraints gbc, int row,
                            String label, Component input) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(input, gbc);
    }

    private void addConference() {
        Conference conference = showConferenceForm("Add Conference", null);
        if (conference != null) {
            conferences.add(conference);
            saveData();
            refreshList(conferences.size() - 1);
            JOptionPane.showMessageDialog(frame, "Conference added successfully.");
        }
    }

    private void updateConference() {
        int index = selectedIndex();
        if (index < 0) return;
        Conference updated = showConferenceForm("Update Conference", conferences.get(index));
        if (updated != null) {
            conferences.set(index, updated);
            saveData();
            refreshList(index);
            JOptionPane.showMessageDialog(frame, "Conference updated successfully.");
        }
    }

    private void deleteConference() {
        int index = selectedIndex();
        if (index < 0) return;
        int answer = JOptionPane.showConfirmDialog(frame,
                "Are you sure you want to delete '" + conferences.get(index).name + "'?",
                "Confirm deletion", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            conferences.remove(index);
            saveData();
            refreshList(-1);
        }
    }

    private static class SurfacePanel extends JPanel {
        private Color surfaceColor;
        private Color borderColor;

        SurfacePanel(Color surfaceColor, Color borderColor) {
            this.surfaceColor = surfaceColor;
            this.borderColor = borderColor;
            setOpaque(false);
            setBorder(new EmptyBorder(10, 10, 10, 10));
        }

        void setColors(Color surfaceColor, Color borderColor) {
            this.surfaceColor = surfaceColor;
            this.borderColor = borderColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 18;
            g2.setColor(surfaceColor);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
        }
    }

    private static class GradientPanel extends JPanel {
        private Color startColor;
        private Color endColor;

        GradientPanel(Color startColor, Color endColor) {
            this.startColor = startColor;
            this.endColor = endColor;
            setOpaque(false);
        }

        void setColors(Color startColor, Color endColor) {
            this.startColor = startColor;
            this.endColor = endColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, startColor, getWidth(), 0, endColor));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    private void applyTheme() {
        Color background = darkMode ? new Color(15, 21, 30) : new Color(240, 244, 250);
        Color panelBackground = darkMode ? new Color(24, 33, 44) : new Color(255, 255, 255);
        Color foreground = darkMode ? new Color(238, 242, 247) : new Color(27, 35, 48);
        Color mutedForeground = darkMode ? new Color(180, 191, 205) : new Color(95, 110, 125);
        Color borderColor = darkMode ? new Color(58, 72, 85) : new Color(218, 225, 235);

        frame.getContentPane().setBackground(background);
        if (searchField != null) {
            searchField.setBackground(panelBackground);
            searchField.setForeground(foreground);
            searchField.setCaretColor(foreground);
        }
        if (countLabel != null) {
            countLabel.setForeground(mutedForeground);
        }
        if (sectionTitle != null) {
            sectionTitle.setForeground(foreground);
        }
        if (listHeading != null) {
            listHeading.setForeground(mutedForeground);
        }
        if (detailsHeading != null) {
            detailsHeading.setForeground(mutedForeground);
        }
        for (Component component : frame.getContentPane().getComponents()) {
            updateSurfaceColors(component, panelBackground, borderColor);
        }
        conferenceList.setBackground(panelBackground);
        conferenceList.setForeground(foreground);
        conferenceList.setSelectionBackground(darkMode ? new Color(54, 92, 145) : new Color(219, 234, 255));
        conferenceList.setSelectionForeground(foreground);
        conferenceList.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        detailsArea.setBackground(panelBackground);
        detailsArea.setForeground(foreground);
        detailsArea.setCaretColor(foreground);
        detailsArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        UIManager.put("Button.disabledText", mutedForeground);
        UIManager.put("Label.foreground", foreground);
        UIManager.put("OptionPane.messageForeground", foreground);
        UIManager.put("TextArea.background", panelBackground);
        UIManager.put("TextArea.foreground", foreground);
        UIManager.put("TextField.background", panelBackground);
        UIManager.put("TextField.foreground", foreground);

        if (headerPanel != null) {
            headerPanel.setColors(
                    darkMode ? new Color(22, 34, 46) : new Color(38, 90, 214),
                    darkMode ? new Color(47, 86, 160) : new Color(87, 151, 255));
        }

        if (frame.getRootPane() != null) {
            frame.getRootPane().setBackground(background);
        }
        frame.repaint();
    }

    private void updateSurfaceColors(Component component, Color surface, Color border) {
        if (component instanceof SurfacePanel) {
            ((SurfacePanel) component).setColors(surface, border);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                updateSurfaceColors(child, surface, border);
            }
        }
    }

    private JButton makeButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setRolloverEnabled(true);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });
        return button;
    }

    private void createWindow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 620);
        frame.setMinimumSize(new Dimension(700, 500));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(new Color(240, 244, 250));

        headerPanel = new GradientPanel(
                darkMode ? new Color(22, 34, 46) : new Color(38, 90, 214),
                darkMode ? new Color(47, 86, 160) : new Color(87, 151, 255));
        headerPanel.setLayout(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(20, 20, 18, 20));

        JLabel heading = new JLabel("Conference Management System", SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 24));
        heading.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Plan events, venues, and sessions in one place.", SwingConstants.CENTER);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(new Color(230, 236, 255));

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.add(heading);
        titleStack.add(Box.createVerticalStrut(6));
        titleStack.add(subtitle);
        headerPanel.add(titleStack, BorderLayout.CENTER);

        conferenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conferenceList.setFixedCellHeight(42);
        conferenceList.setFont(new Font("SansSerif", Font.PLAIN, 14));
        conferenceList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                label.setFont(new Font("SansSerif", Font.PLAIN, 14));
                return label;
            }
        });
        conferenceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int index = conferenceList.getSelectedIndex();
                if (index >= 0 && index < visibleConferences.size()) {
                    detailsArea.setText(visibleConferences.get(index).details());
                    detailsArea.setCaretPosition(0);
                }
            }
        });

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        detailsArea.setBorder(new EmptyBorder(12, 12, 12, 12));

        JScrollPane listScrollPane = new JScrollPane(conferenceList);
        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane detailsScrollPane = new JScrollPane(detailsArea);
        detailsScrollPane.setBorder(BorderFactory.createEmptyBorder());

        listHeading = new JLabel("YOUR CONFERENCES");
        listHeading.setFont(new Font("SansSerif", Font.BOLD, 12));
        detailsHeading = new JLabel("CONFERENCE DETAILS");
        detailsHeading.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel listCard = new SurfacePanel(Color.WHITE, new Color(218, 225, 235));
        listCard.setLayout(new BorderLayout(0, 10));
        listCard.add(listHeading, BorderLayout.NORTH);
        listCard.add(listScrollPane, BorderLayout.CENTER);

        JPanel detailsCard = new SurfacePanel(Color.WHITE, new Color(218, 225, 235));
        detailsCard.setLayout(new BorderLayout(0, 10));
        detailsCard.add(detailsHeading, BorderLayout.NORTH);
        detailsCard.add(detailsScrollPane, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listCard, detailsCard);
        splitPane.setResizeWeight(0.36);
        splitPane.setBorder(BorderFactory.createEmptyBorder(4, 18, 12, 18));
        splitPane.setOpaque(false);
        splitPane.setDividerSize(12);

        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(12, 20, 10, 20));
        sectionTitle = new JLabel("Conference dashboard");
        sectionTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        JPanel searchArea = new JPanel(new BorderLayout(10, 0));
        searchArea.setOpaque(false);
        JLabel searchLabel = new JLabel("Search");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        searchField = new JTextField(22);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchField.setToolTipText("Search by conference name, date, or venue");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 215, 228), 1),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        countLabel = new JLabel();
        countLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        searchArea.add(searchLabel, BorderLayout.WEST);
        searchArea.add(searchField, BorderLayout.CENTER);
        searchArea.add(countLabel, BorderLayout.EAST);
        toolbar.add(sectionTitle, BorderLayout.WEST);
        toolbar.add(searchArea, BorderLayout.EAST);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { refreshList(-1); }
            @Override
            public void removeUpdate(DocumentEvent e) { refreshList(-1); }
            @Override
            public void changedUpdate(DocumentEvent e) { refreshList(-1); }
        });

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        workspace.add(toolbar, BorderLayout.NORTH);
        workspace.add(splitPane, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 12));
        buttons.setBorder(new EmptyBorder(4, 12, 8, 20));
        buttons.setOpaque(false);
        JButton addButton = makeButton("+  Add conference", new Color(37, 99, 235));
        JButton updateButton = makeButton("✎  Edit", new Color(71, 85, 105));
        JButton deleteButton = makeButton("×  Delete", new Color(185, 55, 65));
        JButton themeButton = makeButton("◐  Dark mode", new Color(71, 85, 105));
        addButton.addActionListener(e -> addConference());
        updateButton.addActionListener(e -> updateConference());
        deleteButton.addActionListener(e -> deleteConference());
        themeButton.addActionListener(e -> {
            darkMode = !darkMode;
            applyTheme();
            themeButton.setText(darkMode ? "☀  Light mode" : "◐  Dark mode");
        });
        buttons.add(addButton);
        buttons.add(updateButton);
        buttons.add(deleteButton);
        buttons.add(themeButton);

        frame.add(headerPanel, BorderLayout.NORTH);
        frame.add(workspace, BorderLayout.CENTER);
        frame.add(buttons, BorderLayout.SOUTH);
        refreshList(-1);
        applyTheme();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ConferenceManagementSystem app = new ConferenceManagementSystem();
            app.loadData();
            app.createWindow();
        });
    }
}