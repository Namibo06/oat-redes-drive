package drive.rede.com.br;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientTCP {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {


        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream());
             Scanner scanner = new Scanner(System.in)) {


            System.out.println(input.readUTF());
            output.writeUTF(scanner.nextLine());

            System.out.println(input.readUTF());
            output.writeUTF(scanner.nextLine());

            String response = input.readUTF();
            System.out.println(response);
            if (!response.contains("Login bem-sucedido")) {
                return;
            }

            while (true) {
                System.out.println("\nDigite um comando (LIST, UPLOAD, DOWNLOAD, EXIT):");
                String command = scanner.nextLine().toUpperCase();

                if (command.isBlank()) {
                    System.out.println("Comando vazio! Por favor, digite novamente.");
                    continue;
                }

                try {
                    output.writeUTF(command);

                    switch (command) {
                        case "LIST":
                            String file;
                            while (!(file = input.readUTF()).equals("END")) {
                                System.out.println(file);
                            }
                            break;

                        case "UPLOAD":
                            uploadFile(socket, output, input, scanner);
                            break;

                        case "DOWNLOAD":
                            downloadFile(output, input, scanner);
                            break;

                        case "EXIT":
                            System.out.println("Encerrando conexão...");
                            return;

                        default:
                            System.out.println(input.readUTF());
                    }
                } catch (IOException e) {
                    System.out.println("Erro na comunicação: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Erro na comunicação: " + e.getMessage());
        }
    }

    private static void uploadFile(Socket socket, DataOutputStream output,
                                   DataInputStream input, Scanner scanner) throws IOException {
        System.out.println("Digite o caminho do arquivo para upload:");
        String filePath = scanner.nextLine();

        if (filePath.isBlank()) {
            System.out.println("Caminho inválido.");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("Arquivo não encontrado!");
            return;
        }

        output.writeUTF(file.getName());
        output.writeLong(file.length());
        output.flush();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();
        }

        System.out.println(input.readUTF());
        System.out.println(input.readUTF());
    }

    private static void downloadFile(DataOutputStream output, DataInputStream input, Scanner scanner) throws IOException {
        System.out.println("Digite o nome do arquivo para download:");
        String fileName = scanner.nextLine();
        output.writeUTF(fileName);
        output.flush();

        String serverResponse = input.readUTF();
        if (serverResponse.startsWith("Erro:")) {
            System.out.println(serverResponse);
            return;
        }

        // Se chegou aqui, o arquivo existe
        long fileSize = input.readLong();
        File file = new File("downloads/" + fileName);
        file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            byte[] buffer = new byte[4096];
            long received = 0;
            int bytesRead;
            while (received < fileSize &&
                    (bytesRead = input.read(buffer, 0, (int)Math.min(buffer.length, fileSize - received))) != -1) {
                bos.write(buffer, 0, bytesRead);
                received += bytesRead;
            }
        }

        System.out.println("Download concluído! Arquivo salvo em: " + file.getAbsolutePath());
    }
}
