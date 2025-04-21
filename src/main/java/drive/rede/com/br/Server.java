package drive.rede.com.br;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.HashMap;

public class Server {
    private static final int PORT = 12345;
    private static final String STORAGE_DIR = "armazenamento";
    private static final HashMap<String, String> users = new HashMap<>();

    static {
        users.put("user1", "password1");
        users.put("user2", "password2");
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor rodando na porta " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

                output.writeUTF("Digite o usuário:");
                String user = input.readUTF();

                output.writeUTF("Digite a senha:");
                String password = input.readUTF();

                if (authenticate(user, password)) {
                    username = user;
                    setupUserDirectories();
                    output.writeUTF("Login bem-sucedido! Opções: LIST, UPLOAD, DOWNLOAD, EXIT");

                    String command;
                    while (true) {
                        command = input.readUTF();
                        switch (command.toUpperCase()) {
                            case "LIST":
                                for (String file : listFiles().split("\n")) {
                                    output.writeUTF(file);
                                }
                                break;
                            case "UPLOAD":
                                receiveFile(input, output);
                                break;
                            case "DOWNLOAD":
                                sendFile(output, input);
                                break;
                            default:
                                output.writeUTF("Comando inválido!");
                                break;
                        }
                        output.writeUTF("READY");
                    }
                } else {
                    output.writeUTF("Autenticação falhou!");
                }

            } catch (IOException e) {
                System.err.println("Erro no cliente: " + e.getMessage());
                System.err.println("Erro no cliente: " + e.fillInStackTrace());
                System.err.println("Erro no cliente: " + e);
            }
        }

        private boolean authenticate(String user, String password) {
            return users.containsKey(user) && users.get(user).equals(password);
        }

        private void setupUserDirectories() {
            Path userPath = Paths.get(STORAGE_DIR, username);
            try {
                Files.createDirectories(userPath.resolve("pdf"));
                Files.createDirectories(userPath.resolve("jpg"));
                Files.createDirectories(userPath.resolve("txt"));
            } catch (IOException e) {
                System.err.println("Erro ao criar diretórios do usuário: " + e.getMessage());
            }
        }

        private String listFiles() {
            Path userPath = Paths.get(STORAGE_DIR, username);
            StringBuilder fileList = new StringBuilder();
            try {
                Files.walk(userPath, 2)
                        .filter(Files::isRegularFile)
                        .forEach(file -> fileList.append(file.getFileName()).append("\n"));

                if (fileList.length() == 0) {
                    fileList.append("Nenhum arquivo encontrado\n");
                }
            } catch (IOException e) {
                return "Erro ao listar arquivos\n";
            }
            fileList.append("END");
            return fileList.toString();
        }

        private void receiveFile(DataInputStream input, DataOutputStream output) throws IOException {
            String fileName = input.readUTF();
            long fileSize = input.readLong();
            System.out.println("Recebendo: " + fileName + " (" + fileSize + " bytes)");

            String fileType = getFileExtension(fileName);
            Path filePath = Paths.get(STORAGE_DIR, username, fileType, fileName);
            Files.createDirectories(filePath.getParent());

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile());
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] buffer = new byte[4096];
                long received = 0;
                int read;
                while (received < fileSize &&
                        (read = input.read(buffer, 0, (int) Math.min(buffer.length, fileSize - received))) != -1) {
                    bos.write(buffer, 0, read);
                    received += read;
                }
            }

            output.writeUTF("UPLOAD_COMPLETED");
            output.writeUTF("READY");
            output.flush();
            System.out.println("Arquivo salvo em: " + filePath);
        }

        private void sendFile(DataOutputStream output, DataInputStream input) throws IOException {
            String fileName = input.readUTF(); // Já recebemos o nome do arquivo antes
            String fileType = getFileExtension(fileName);
            Path filePath = Paths.get(STORAGE_DIR, username, fileType, fileName);

            if (Files.exists(filePath)) {
                output.writeUTF("Arquivo encontrado. Iniciando download...");
                output.writeLong(Files.size(filePath)); // Envia o tamanho do arquivo primeiro

                try (InputStream fileInput = Files.newInputStream(filePath)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fileInput.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                    output.flush();
                }
            } else {
                output.writeUTF("Erro: Arquivo não encontrado");
            }
        }

        private String getFileExtension(String fileName) {
            if (fileName.endsWith(".pdf")) return "pdf";
            if (fileName.endsWith(".jpg")) return "jpg";
            if (fileName.endsWith(".txt")) return "txt";
            return "outros";
        }
    }
}
