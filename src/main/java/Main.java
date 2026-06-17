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

            if (input.equals("exit")) {
                break;
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
                    System.out.println(handleEcho(arguments));
                    break;
                case "type":
                    type(commandList, arguments);
                    break;
                case "pwd":
                    System.out.println(System.getProperty("user.dir"));
                    break;
                case "cd":
                    // Resolve paths relative to the shell's current directory tracked in user.dir
                    String currentDir = System.getProperty("user.dir");
                    String target = arguments == null ? "" : arguments;
                    // Check HOME environment variable first (for shell compatibility), fall back to
                    // user.home
                    String home = System.getenv("HOME");
                    if (home == null) {
                        home = System.getProperty("user.home");
                    }

                    // Support `cd` with no args -> go to home, and ~ expansion
                    if (target.isEmpty() || "~".equals(target)) {
                        target = home;
                    } else if (target.startsWith("~/")) {
                        target = home + target.substring(1);
                    }

                    File file = new File(target);
                    if (!file.isAbsolute()) {
                        file = new File(currentDir, target);
                    }
                    try {
                        String canonical = file.getCanonicalPath();
                        File canonicalFile = new File(canonical);
                        if (canonicalFile.exists() && canonicalFile.isDirectory()) {
                            System.setProperty("user.dir", canonical);
                        } else {
                            System.out.println("cd: " + arguments + ": No such file or directory");
                        }
                    } catch (IOException e) {
                        if (file.exists() && file.isDirectory()) {
                            System.setProperty("user.dir", file.getAbsolutePath());
                        } else {
                            System.out.println("cd: " + arguments + ": No such file or directory");
                        }
                    }
                    break;
                default:
                    executeExternalCommand(input); // Execute external programs with their own command and argument
            }
        }
    }

    public static String parseQuotedString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Handle single-quoted strings
        if (input.startsWith("'") && input.endsWith("'") && input.length() >= 2) {
            return input.substring(1, input.length() - 1);
        }

        // Handle double-quoted strings
        if (input.startsWith("\"") && input.endsWith("\"") && input.length() >= 2) {
            return input.substring(1, input.length() - 1);
        }

        // Return as-is if not quoted
        return input;
    }

    public static String handleEcho(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        StringBuilder currentToken = new StringBuilder();
        boolean lastWasSpace = false;

        while (i < arguments.length()) {
            char c = arguments.charAt(i);

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                i++;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                i++;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                // Outside quotes: collapse whitespace
                if (currentToken.length() > 0) {
                    result.append(currentToken);
                    currentToken = new StringBuilder();
                    lastWasSpace = true;
                }
                // Skip all consecutive whitespace
                while (i < arguments.length() && Character.isWhitespace(arguments.charAt(i))) {
                    i++;
                }
            } else {
                // Inside or outside quotes: accumulate character
                if (lastWasSpace && result.length() > 0) {
                    result.append(" ");
                    lastWasSpace = false;
                }
                currentToken.append(c);
                i++;
            }
        }

        // Add any remaining token
        if (currentToken.length() > 0) {
            result.append(currentToken);
        }

        return result.toString();
    }

    public static void type(List<String> commandList, String arguments) {
        if (commandList.contains(arguments)) {
            System.out.println(arguments + " is a shell builtin");
        } else {
            String pathDir = System.getenv("PATH");
            if (pathDir == null) {
                pathDir = "";
            }
            String[] dirs = pathDir.split(":");
            boolean found = false;

            for (String dir : dirs) {
                File file = new File(dir, arguments);

                // Only consider files that exist and are executable
                if (file.exists() && file.canExecute()) {
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

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream())); // Process
                                                                                                                 // output

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println(input + ": command not found");
        }
    }
}
