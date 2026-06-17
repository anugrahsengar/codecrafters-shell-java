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
                    handleEchoCommand(input);
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

    public static void handleEchoCommand(String input) {
        // Parse echo command with potential redirection
        List<String> parts = parseCommandLineWithRedirection(input);
        if (parts.isEmpty()) {
            return;
        }

        // Remove "echo" command from parts
        parts.remove(0);

        String outputFile = null;
        boolean append = false;

        // Check for output redirection (>, 1>, >>, 1>>, 2>, 2>>)
        for (int i = 0; i < parts.size(); i++) {
            String arg = parts.get(i);
            if ((arg.equals(">") || arg.equals("1>") || arg.equals(">>") || arg.equals("1>>") || arg.equals("2>")
                    || arg.equals("2>>")) && i + 1 < parts.size()) {
                outputFile = parts.get(i + 1);
                append = arg.contains(">>");
                // Remove redirection operator and file from parts
                parts.subList(i, parts.size()).clear();
                break;
            }
        }

        // Reconstruct echo arguments from remaining parts
        StringBuilder echoArgs = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0)
                echoArgs.append(" ");
            echoArgs.append(parts.get(i));
        }

        String output = handleEcho(echoArgs.toString());

        if (outputFile != null) {
            // Write to file
            try {
                java.io.FileWriter writer;
                if (append) {
                    writer = new java.io.FileWriter(outputFile, true);
                } else {
                    writer = new java.io.FileWriter(outputFile, false);
                }
                writer.write(output);
                writer.write("\n");
                writer.close();
            } catch (IOException e) {
                System.out.println("Error writing to file: " + outputFile);
            }
        } else {
            System.out.println(output);
        }
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

            if (c == '\\' && !inSingleQuote && i + 1 < arguments.length()) {
                // Backslash escape outside quotes - add the escaped character
                i++;
                char nextChar = arguments.charAt(i);
                if (lastWasSpace && result.length() > 0) {
                    result.append(" ");
                    lastWasSpace = false;
                }
                currentToken.append(nextChar);
                i++;
            } else if (c == '\'' && !inDoubleQuote) {
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
        // Parse command and check for redirection
        List<String> parts = parseCommandLineWithRedirection(input);
        if (parts.isEmpty()) {
            return;
        }

        String outputFile = null;
        String errorFile = null;
        boolean append = false;
        boolean appendError = false;
        List<String> args = new java.util.ArrayList<>(parts);

        // Check for output redirection (>, 1>, >>, 1>>, 2>, 2>>)
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ((arg.equals(">") || arg.equals("1>") || arg.equals(">>") || arg.equals("1>>")) && i + 1 < args.size()) {
                outputFile = args.get(i + 1);
                append = arg.contains(">>");
                // Remove redirection operator and file from args
                args.subList(i, args.size()).clear();
                break;
            } else if ((arg.equals("2>") || arg.equals("2>>")) && i + 1 < args.size()) {
                errorFile = args.get(i + 1);
                appendError = arg.contains(">>");
                // Remove redirection operator and file from args
                args.subList(i, args.size()).clear();
                break;
            }
        }

        if (args.isEmpty()) {
            return;
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(args);

            // Only redirect error stream to stdout if neither stdout nor stderr are
            // redirected
            if (outputFile == null && errorFile == null) {
                processBuilder.redirectErrorStream(true);
            }

            if (outputFile != null) {
                // Redirect stdout to file
                File outFile = new File(outputFile);
                if (append) {
                    processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                } else {
                    processBuilder.redirectOutput(outFile);
                }
            }

            if (errorFile != null) {
                // Redirect stderr to file
                File errFile = new File(errorFile);
                if (appendError) {
                    processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                } else {
                    processBuilder.redirectError(errFile);
                }
            }

            Process process = processBuilder.start();

            if (outputFile == null) {
                // Only read output if not redirected to file
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            if (errorFile == null && outputFile != null) {
                // If stdout is redirected but stderr is not, read stderr and print it
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                while ((line = errorReader.readLine()) != null) {
                    System.err.println(line);
                }
            }

            process.waitFor();
        } catch (IOException e) {
            System.out.println(input.split(" ")[0] + ": command not found");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static List<String> parseCommandLineWithRedirection(String input) {
        List<String> args = new java.util.ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        int i = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        while (i < input.length()) {
            char c = input.charAt(i);

            if (c == '\\' && !inSingleQuote && i + 1 < input.length()) {
                // Backslash escape outside quotes - add the escaped character
                i++;
                currentArg.append(input.charAt(i));
                i++;
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                i++;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                i++;
            } else if (c == '>' && !inSingleQuote && !inDoubleQuote) {
                // Check for >> or 1> or 2>
                if (currentArg.length() > 0) {
                    String arg = currentArg.toString();
                    // Check if current arg is a file descriptor number
                    if (arg.equals("1") || arg.equals("2")) {
                        // This is a file descriptor before >, so combine them
                        if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                            // This is 1>> or 2>>
                            args.add(arg + ">>");
                            i += 2;
                        } else {
                            // This is 1> or 2>
                            args.add(arg + ">");
                            i++;
                        }
                    } else {
                        // Regular argument followed by redirect
                        args.add(arg);
                        if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                            // >>
                            args.add(">>");
                            i += 2;
                        } else {
                            // >
                            args.add(">");
                            i++;
                        }
                    }
                    currentArg = new StringBuilder();
                } else {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                        // >>
                        args.add(">>");
                        i += 2;
                    } else {
                        // >
                        args.add(">");
                        i++;
                    }
                }
                // Skip whitespace after redirection
                while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
                    i++;
                }
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                // End of argument
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg = new StringBuilder();
                }
                // Skip whitespace
                while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
                    i++;
                }
            } else {
                // Add character to current argument
                currentArg.append(c);
                i++;
            }
        }

        // Add final argument if any
        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args;
    }
}
