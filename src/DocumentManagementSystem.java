import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.sql.*;
import java.util.Arrays;
import java.util.Scanner;


import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;





public class DocumentManagementSystem extends JFrame {
    private JButton uploadButton;
    private JButton downloadButton;
    private JButton previewButton;
    private JTable filesTable;
    private JTextField searchField;
    private JComboBox<String> orderByComboBox;
    private JComboBox<String> orderDirectionComboBox;

    public DocumentManagementSystem() {
        setTitle("Document Management System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Initialize buttons
        uploadButton = new JButton("Upload File");
        uploadButton.addActionListener(e -> uploadFile());

        downloadButton = new JButton("Download Selected File");
        downloadButton.addActionListener(e -> downloadFile());

        previewButton = new JButton("Preview");
        previewButton.addActionListener(e -> previewSelectedFile());

        // Initialize search field
        searchField = new JTextField(20);
        searchField.addActionListener(e -> searchFiles(searchField.getText()));

        // Initialize order by combo box
        orderByComboBox = new JComboBox<>(new String[]{"Name", "Size", "Upload Time", "Type"});
        orderByComboBox.addActionListener(e -> loadFilesFromDatabase());

        // Initialize order direction combo box
        orderDirectionComboBox = new JComboBox<>(new String[]{"Ascending", "Descending"});
        orderDirectionComboBox.addActionListener(e -> loadFilesFromDatabase());

        // Button panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(uploadButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(previewButton);
        buttonPanel.add(new JLabel("Search: "));
        buttonPanel.add(searchField);
        buttonPanel.add(new JLabel("Order By: "));
        buttonPanel.add(orderByComboBox);
        buttonPanel.add(new JLabel("Order Direction: "));
        buttonPanel.add(orderDirectionComboBox);

        // Layout setup
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(buttonPanel, BorderLayout.NORTH);

        // Table setup
        filesTable = new JTable();
        JScrollPane scrollPane = new JScrollPane(filesTable);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        // Frame setup
        pack();
        setLocationRelativeTo(null);

        // Load files from the database when the application starts
        loadFilesFromDatabase();
    }

    private void uploadFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/files", "root", "dil2001ruk")) {
                String sql = "INSERT INTO filedatabase (name, type, content, upload_time, file_size, author) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, selectedFile.getName());
                    statement.setString(2, getFileExtension(selectedFile));
                    statement.setBlob(3, new FileInputStream(selectedFile));
                    statement.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                    statement.setLong(5, selectedFile.length());
                    statement.setString(6, "Your Author"); // Change this to capture the author
                    statement.executeUpdate();
                    JOptionPane.showMessageDialog(this, "File uploaded successfully");
                    // Reload files from the database to update the table
                    loadFilesFromDatabase();
                }
            } catch (SQLException | FileNotFoundException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error uploading file: " + ex.getMessage());
            }
        }
    }

    private void downloadFile() {
        int selectedRow = filesTable.getSelectedRow();
        if (selectedRow != -1) {
            String fileName = (String) filesTable.getValueAt(selectedRow, 0);
            try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/files", "root", "dil2001ruk")) {
                String sql = "SELECT content FROM filedatabase WHERE name = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, fileName);
                    ResultSet resultSet = statement.executeQuery();
                    if (resultSet.next()) {
                        Blob blob = resultSet.getBlob("content");
                        InputStream inputStream = blob.getBinaryStream();

                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setSelectedFile(new File(fileName));
                        int result = fileChooser.showSaveDialog(this);
                        if (result == JFileChooser.APPROVE_OPTION) {
                            File outputFile = fileChooser.getSelectedFile();
                            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                                JOptionPane.showMessageDialog(this, "File downloaded successfully");
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                JOptionPane.showMessageDialog(this, "Error downloading file: " + ex.getMessage());
                            }
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, "File not found in the database");
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error downloading file: " + ex.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(this, "Please select a file to download");
        }
    }

    private void previewSelectedFile() {
        int selectedRow = filesTable.getSelectedRow();
        if (selectedRow != -1) {
            String fileName = (String) filesTable.getValueAt(selectedRow, 0);
            String fileSize = (String) filesTable.getValueAt(selectedRow, 3);
            Timestamp uploadTime = (Timestamp) filesTable.getValueAt(selectedRow, 2);

            try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/files", "root", "dil2001ruk")) {
                String sql = "SELECT type, content FROM filedatabase WHERE name = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, fileName);
                    ResultSet resultSet = statement.executeQuery();
                    if (resultSet.next()) {
                        String fileType = resultSet.getString("type");
                        Blob contentBlob = resultSet.getBlob("content");

                        JFrame previewFrame = new JFrame("File Preview");
                        previewFrame.setLayout(new BorderLayout());
                        JLabel fileInfoLabel = new JLabel("Filename: " + fileName + ", Size: " + fileSize + ", Uploaded: " + uploadTime);
                        fileInfoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                        previewFrame.add(fileInfoLabel, BorderLayout.NORTH);

                        // Preview based on file type
                        if (fileType.equalsIgnoreCase("pdf")) {
                            previewPDFFile(contentBlob, previewFrame);
                        } else if (Arrays.asList("png", "jpg", "jpeg").contains(fileType.toLowerCase())) {
                            previewImageFile(contentBlob, previewFrame);  // Updated method call
                        } else {
                            JOptionPane.showMessageDialog(this, "Unsupported file preview for file: " + fileName);
                        }

                        previewFrame.pack();
                        previewFrame.setLocationRelativeTo(null);
                        previewFrame.setVisible(true);

                    } else {
                        JOptionPane.showMessageDialog(this, "File not found in the database");
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error previewing file: " + ex.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(this, "Please select a file to preview");
        }
    }


    private void previewTextFile(Blob contentBlob) {
        try {
            InputStream inputStream = contentBlob.getBinaryStream();
            StringBuilder previewText = new StringBuilder();
            Scanner scanner = new Scanner(inputStream);
            while (scanner.hasNextLine()) {
                previewText.append(scanner.nextLine()).append("\n");
            }
            // Show the preview text in a dialog or dedicated area in your UI
            JTextArea previewArea = new JTextArea(previewText.toString());
            previewArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(previewArea);
            JOptionPane.showMessageDialog(this, scrollPane, "File Preview", JOptionPane.PLAIN_MESSAGE);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void previewPDFFile(Blob contentBlob, JFrame previewFrame) {
        try {
            // Convert Blob to InputStream
            InputStream pdfInputStream = contentBlob.getBinaryStream();

            // Create a RandomAccessReadBuffer from the InputStream
            RandomAccessRead randomAccessRead = new RandomAccessReadBuffer(pdfInputStream);

            // Load the PDF document from the RandomAccessRead
            PDDocument document = Loader.loadPDF(randomAccessRead);

            PDFRenderer renderer = new PDFRenderer(document);

            // Render the first page to an image
            BufferedImage image = renderer.renderImageWithDPI(0, 96); // Render with a DPI of 96

            // Display the image in a JLabel
            ImageIcon imageIcon = new ImageIcon(image);
            JLabel pdfLabel = new JLabel(imageIcon);
            JScrollPane scrollPane = new JScrollPane(pdfLabel);

            previewFrame.add(scrollPane, BorderLayout.CENTER);

            document.close(); // Don't forget to close the document
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error previewing PDF file: " + ex.getMessage());
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error previewing PDF file: " + ex.getMessage());
        }
    }




    private void previewImageFile(Blob contentBlob, JFrame previewFrame) {
        try {
            byte[] imageBytes = contentBlob.getBytes(1, (int) contentBlob.length());
            ImageIcon imageIcon = new ImageIcon(imageBytes);
            JLabel imageLabel = new JLabel(imageIcon);
            // Resize image if needed to fit in a reasonable preview size
            imageLabel.setSize(300, 200); // Adjust dimensions as needed
            JScrollPane scrollPane = new JScrollPane(imageLabel);
            previewFrame.add(scrollPane, BorderLayout.CENTER);
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error reading image file content");
        }
    }


    private String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOfDot = name.lastIndexOf('.');
        if (lastIndexOfDot != -1 && lastIndexOfDot < name.length() - 1) {
            return name.substring(lastIndexOfDot + 1);
        }
        return "";
    }

    private void loadFilesFromDatabase() {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/files", "root", "dil2001ruk")) {
            String orderBy = null;
            switch (orderByComboBox.getSelectedItem().toString()) {
                case "Name":
                    orderBy = "name";
                    break;
                case "Size":
                    orderBy = "file_size";
                    break;
                case "Upload Time":
                    orderBy = "upload_time";
                    break;
                case "Type":
                    orderBy = "type";
                    break;
            }
            String orderDirection = orderDirectionComboBox.getSelectedItem().toString().equalsIgnoreCase("Ascending") ? "ASC" : "DESC";

            String sql = "SELECT name, type, upload_time, file_size, author FROM filedatabase ORDER BY " + orderBy + " " + orderDirection;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                ResultSet resultSet = statement.executeQuery();
                DefaultTableModel model = new DefaultTableModel(new Object[]{"File Name", "Type", "Upload Time", "File Size", "Author"}, 0);
                while (resultSet.next()) {
                    long fileSize = resultSet.getLong("file_size");
                    String fileSizeStr = formatFileSize(fileSize); // Convert file size to human-readable format
                    model.addRow(new Object[]{
                            resultSet.getString("name"),
                            resultSet.getString("type"),
                            resultSet.getTimestamp("upload_time"),
                            fileSizeStr,
                            resultSet.getString("author")
                    });
                }
                filesTable.setModel(model);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading files from database: " + ex.getMessage());
        }
    }

    private void searchFiles(String searchString) {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/files", "root", "dil2001ruk")) {
            String sql = "SELECT name, type, upload_time, file_size, author FROM filedatabase WHERE name LIKE ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, "%" + searchString + "%");
                ResultSet resultSet = statement.executeQuery();
                DefaultTableModel model = new DefaultTableModel(new Object[]{"File Name", "Type", "Upload Time", "File Size", "Author"}, 0);
                while (resultSet.next()) {
                    long fileSize = resultSet.getLong("file_size");
                    String fileSizeStr = formatFileSize(fileSize); // Convert file size to human-readable format
                    model.addRow(new Object[]{
                            resultSet.getString("name"),
                            resultSet.getString("type"),
                            resultSet.getTimestamp("upload_time"),
                            fileSizeStr,
                            resultSet.getString("author")
                    });
                }
                filesTable.setModel(model);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error searching files: " + ex.getMessage());
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", (double) size / 1024);
        } else {
            return String.format("%.2f MB", (double) size / (1024 * 1024));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new DocumentManagementSystem().setVisible(true);
        });
    }
}
