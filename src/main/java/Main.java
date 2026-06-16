import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);
        List<String> commandList = Arrays.asList("echo", "exit", "type", "pwd", "cd"); // List of possible commands

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                System.out.println("Please enter a non-empty command.");
                continue;
            }

            // Splitting command and arguments
            String[] parts = input.split(" ", 2);
            String command = parts[0];
            String arguments = parts.length > 1 ? parts[1] : "";

            if (input.equals("exit 0")) { // Exit with code 0
                break;
            }

            // Check for type of command
            switch (command) {
                case "echo":
                    System.out.println(arguments);
                    break;
                case "type":
                    type(commandList, arguments);
                    break;
                case "pwd":
                    System.out.println(System.getProperty("user.dir"));
                    break;
                case "cd":
                    File file = new File(arguments);
                    if (file.exists() && file.isDirectory()) {
                        System.setProperty("user.dir", file.getAbsolutePath());
                    } else {
                        System.out.println("cd: " + arguments + ": No such file or directory");
                    }
                    break;
                default:
                    executeExternalCommand(input); // Execute external programs with their own command and argument
            }
        }
    }

    public static void type(List<String> commandList, String arguments) {
        if (commandList.contains(arguments)) {
            System.out.println(arguments + " is a shell builtin");
        } else {
            String pathDir = System.getenv("PATH");
            String[] dirs = pathDir.split(":");
            boolean found = false;

            for (String dir : dirs) {
                File file = new File(dir, arguments);

                if (file.exists()) {
                    System.out.println(arguments + " is " + file.getAbsolutePath());
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.println(arguments + ": not found");
            }
        }
    }

    public static void executeExternalCommand(String input) {
        String[] externalProgram = input.split(" ");
        try {

            ProcessBuilder processBuilder = new ProcessBuilder(externalProgram); // Execute program
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader bufferedReader = new BufferedReader
                    (new InputStreamReader(process.getInputStream())); // Process output

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println(input + ": command not found");
        }
    }
}
