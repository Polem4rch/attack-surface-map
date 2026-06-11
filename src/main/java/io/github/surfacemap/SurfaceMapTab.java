package io.github.surfacemap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.swing.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class SurfaceMapTab {

    private final MontoyaApi      api;
    private final SurfaceMapModel model;
    private final SwingUtils      swingUtils;
    private SurfaceMapHandler     handler;

    private final JPanel    root;
    private final JTextArea log        = new JTextArea();
    private final JLabel    countLabel = new JLabel("0 endpoints");
    private final JCheckBox captureBox = new JCheckBox("Capture (proxy, in-scope)");

    public SurfaceMapTab(MontoyaApi api, SurfaceMapModel model) {
        this.api        = api;
        this.model      = model;
        this.swingUtils = api.userInterface().swingUtils();
        this.root       = buildUI();
    }

    public void setHandler(SurfaceMapHandler h) {
        this.handler = h;
    }

    public Component component() {
        return root;
    }

    // -------------------------------------------------------------------------

    private JPanel buildUI() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

        captureBox.addActionListener(e -> onCaptureToggle());

        JButton btnOpen   = new JButton("Open map in browser");
        JButton btnBase   = new JButton("Set baseline");
        JButton btnClear  = new JButton("Clear all");
        JButton btnExport = new JButton("Export project");
        JButton btnImport = new JButton("Import project");

        btnOpen  .addActionListener(e -> onOpen());
        btnBase  .addActionListener(e -> onBaseline());
        btnClear .addActionListener(e -> onClear());
        btnExport.addActionListener(e -> onExport());
        btnImport.addActionListener(e -> onImport());

        countLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        for (Component c : new Component[]{captureBox, btnOpen, btnBase,
                btnClear, btnExport, btnImport, countLabel}) {
            toolbar.add(c);
        }

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setText("Surface Map ready.\n" +
                "1. Define your target in Target > Scope.\n" +
                "2. Tick Capture, then browse the app through Burp's proxy,\n" +
                "   opening every menu, tab and screen you can find.\n" +
                "3. Click 'Open map in browser' to see the visual map.\n");

        panel.add(toolbar,              BorderLayout.NORTH);
        panel.add(new JScrollPane(log), BorderLayout.CENTER);
        return panel;
    }

    // -------------------------------------------------------------------------

    private void onCaptureToggle() {
        boolean on = captureBox.isSelected();
        if (handler != null) handler.setCapturing(on);
        appendLog(on
                ? "Capture ON – browse the target, open every menu and tab."
                : "Capture OFF.");
    }

    private void onBaseline() {
        model.setBaseline();
        appendLog("Baseline set. Endpoints from now on will be marked NEW.");
    }

    private void onClear() {
        int choice = JOptionPane.showConfirmDialog(
                swingUtils.suiteFrame(),
                "Clear all captured endpoints?",
                "Surface Map", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            model.clear();
            SwingUtilities.invokeLater(() -> countLabel.setText("0 endpoints"));
            appendLog("Cleared all captured endpoints.");
        }
    }

    private void onExport() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("surface_project.json"));
        if (fc.showSaveDialog(swingUtils.suiteFrame()) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        try {
            model.exportTo(file);
            appendLog("Exported " + model.size() + " endpoints → " + file.getAbsolutePath());
        } catch (Exception ex) {
            appendLog("Export failed: " + ex.getMessage());
            api.logging().logToError("Surface Map export: " + ex.getMessage());
        }
    }

    private void onImport() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(swingUtils.suiteFrame()) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        try {
            int added = model.importFrom(file);
            SwingUtilities.invokeLater(() -> countLabel.setText(model.size() + " endpoints"));
            appendLog("Imported " + added + " new endpoints from " + file.getName()
                    + " (" + model.size() + " total).");
        } catch (Exception ex) {
            appendLog("Import failed – is that a Surface Map .json project? (" + ex.getMessage() + ")");
            api.logging().logToError("Surface Map import: " + ex.getMessage());
        }
    }

    private void onOpen() {
        if (model.size() == 0) {
            appendLog("Nothing captured yet. Tick Capture and browse the target.");
            return;
        }
        new Thread(() -> {
            try {
                String html = HtmlRenderer.render(model);
                // Write to home directory — browsers open file:// from home without
                // the "unique origin" security block that affects system temp folders
                File out = new File(System.getProperty("user.home"), "surface_map.html");
                java.nio.file.Files.writeString(out.toPath(), html,
                        java.nio.charset.StandardCharsets.UTF_8);
                try {
                    Desktop.getDesktop().browse(out.toURI());
                    appendLog("Map opened in browser: " + out.getAbsolutePath());
                } catch (Exception ex) {
                    appendLog("Map written (open manually): " + out.getAbsolutePath());
                }
            } catch (Exception ex) {
                appendLog("Failed to write map: " + ex.getMessage());
                api.logging().logToError("Surface Map render: " + ex.getMessage());
            }
        }, "surface-map-render").start();
    }

    // -------------------------------------------------------------------------

    public void logAndRefreshCount(String line) {
        SwingUtilities.invokeLater(() -> {
            log.append("+ " + line + "\n");
            countLabel.setText(model.size() + " endpoints");
        });
    }

    public void refreshCount() {
        SwingUtilities.invokeLater(() -> countLabel.setText(model.size() + " endpoints"));
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> log.append(msg + "\n"));
    }
}
