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


            System.out.println(input.readUTF()); // "Digite o usuário:"
            output.writeUTF(scanner.nextLine());

            System.out.println(input.readUTF()); // "Digite a senha:"
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
        output.flush(); // Adicione este flush para garantir que os metadados sejam enviados

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush(); // Flush final para garantir que todos os dados sejam enviados
        }

        System.out.println(input.readUTF()); // UPLOAD_COMPLETED
        System.out.println(input.readUTF()); // READY
    }

    private static void downloadFile(DataOutputStream output, DataInputStream input, Scanner scanner) throws IOException {
        System.out.println("Digite o nome do arquivo para download:");
        String fileName = scanner.nextLine();
        output.writeUTF(fileName);

        String serverResponse = input.readUTF();
        if (serverResponse.equals("Erro: Arquivo não encontrado")) {
            System.out.println(serverResponse);
            return;
        }

        File file = new File("downloads/" + fileName);
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            String line;
            while (!(line = input.readUTF()).equals("EOF")) {
                writer.write(line);
                writer.newLine();
            }
        }

        System.out.println("Download concluído! Arquivo salvo em: " + file.getAbsolutePath());
    }
}
