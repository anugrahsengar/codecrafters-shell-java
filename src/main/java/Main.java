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
        // Parse echo command, handling quotes and redirection
        // We need to parse this carefully to preserve quote information

        List<String> args = new java.util.ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        int i = 5; // Skip "echo "
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        // Skip spaces after "echo"
        while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
            i++;
        }

        String outputFile = null;

        // Parse arguments until we hit a redirection operator
        while (i < input.length()) {
            char c = input.charAt(i);

            if (c == '\\' && !inSingleQuote && i + 1 < input.length()) {
                char nextChar = input.charAt(i + 1);
                if (inDoubleQuote) {
                    // Inside double quotes: backslash only escapes $, `, ", \, and newline
                    if (nextChar == '$' || nextChar == '`' || nextChar == '"' || nextChar == '\\' || nextChar == '\n') {
                        currentArg.append(nextChar);
                        i += 2;
                    } else {
                        // Keep backslash literally
                        currentArg.append(c);
                        i++;
                    }
                } else {
                    // Outside quotes: escape the next character
                    currentArg.append(nextChar);
                    i += 2;
                }
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                i++;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                i++;
            } else if ((c == '>' || (c == '1' && i + 1 < input.length() && input.charAt(i + 1) == '>')
                    || (c == '2' && i + 1 < input.length() && input.charAt(i + 1) == '>')) && !inSingleQuote
                    && !inDoubleQuote) {
                // Hit a redirection operator
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                }

                // Parse redirection
                if (c == '1' || c == '2') {
                    String redirectOp = input.substring(i, Math.min(i + 3, input.length()));
                    i += redirectOp.length();
                } else {
                    i++;
                    if (i < input.length() && input.charAt(i) == '>') {
                        i++;
                    }
                }

                // Skip spaces
                while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
                    i++;
                }

                // Get filename
                StringBuilder filename = new StringBuilder();
                while (i < input.length() && !Character.isWhitespace(input.charAt(i))) {
                    filename.append(input.charAt(i));
                    i++;
                }
                outputFile = filename.toString();
                break;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg = new StringBuilder();
                }
                i++;
            } else {
                currentArg.append(c);
                i++;
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        // Join arguments with spaces
        StringBuilder echoOutput = new StringBuilder();
        for (int j = 0; j < args.size(); j++) {
            if (j > 0)
                echoOutput.append(" ");
            echoOutput.append(args.get(j));
        }

        if (outputFile != null) {
            // Write to file
            try {
                java.io.FileWriter writer = new java.io.FileWriter(outputFile, false);
                writer.write(echoOutput.toString());
                writer.write("\n");
                writer.close();
            } catch (IOException e) {
                System.out.println("Error writing to file: " + outputFile);
            }
        } else {
            System.out.println(echoOutput.toString());
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

            if (c == '\\' && !inSingleQuote) {
                if (inDoubleQuote && i + 1 < arguments.length()) {
                    // Inside double quotes: backslash only escapes $, `, ", \, and newline
                    char nextChar = arguments.charAt(i + 1);
                    if (nextChar == '$' || nextChar == '`' || nextChar == '"' || nextChar == '\\' || nextChar == '\n') {
                        // Escape the special character
                        if (lastWasSpace && result.length() > 0) {
                            result.append(" ");
                            lastWasSpace = false;
                        }
                        currentToken.append(nextChar);
                        i += 2;
                    } else {
                        // Not a special character, keep the backslash literally
                        if (lastWasSpace && result.length() > 0) {
                            result.append(" ");
                            lastWasSpace = false;
                        }
                        currentToken.append(c);
                        i++;
                    }
                } else if (!inDoubleQuote && i + 1 < arguments.length()) {
                    // Outside quotes: backslash escapes the next character
                    i++;
                    char nextChar = arguments.charAt(i);
                    if (lastWasSpace && result.length() > 0) {
                        result.append(" ");
                        lastWasSpace = false;
                    }
                    currentToken.append(nextChar);
                    i++;
                } else {
                    // Backslash at end of input
                    if (lastWasSpace && result.length() > 0) {
                        result.append(" ");
                        lastWasSpace = false;
                    }
                    currentToken.append(c);
                    i++;
                }
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
