import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            disableRawMode();
        }));

        List<String> commandList = Arrays.asList("echo", "exit", "type", "pwd", "cd", "jobs"); // List of possible commands

        while (true) {
            System.out.print("$ ");
            System.out.flush();
            String input = readRawLine();
            if (input == null) {
                break;
            }
            input = input.trim();

            if (input.isEmpty()) {
                System.out.println("Please enter a non-empty command.");
                continue;
            }

            CommandParseResult parseResult = parseCommandLine(input);
            if (parseResult.args.isEmpty()) {
                continue;
            }

            executeCommand(parseResult, commandList);
        }
    }

    public static class Token {
        public String text;
        public boolean isRedirection;
        
        public Token(String text, boolean isRedirection) {
            this.text = text;
            this.isRedirection = isRedirection;
        }
    }

    public static class CommandParseResult {
        public List<String> args = new java.util.ArrayList<>();
        public String stdoutFile = null;
        public boolean stdoutAppend = false;
        public String stderrFile = null;
        public boolean stderrAppend = false;
    }

    public static CommandParseResult parseCommandLine(String input) {
        List<Token> tokens = new java.util.ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int i = 0;
        
        while (i < input.length()) {
            char c = input.charAt(i);
            
            if (c == '\\' && !inSingleQuote) {
                if (inDoubleQuote) {
                    if (i + 1 < input.length()) {
                        char nextChar = input.charAt(i + 1);
                        if (nextChar == '$' || nextChar == '`' || nextChar == '"' || nextChar == '\\' || nextChar == '\n') {
                            currentToken.append(nextChar);
                            i += 2;
                        } else {
                            currentToken.append(c);
                            i++;
                        }
                    } else {
                        currentToken.append(c);
                        i++;
                    }
                } else {
                    if (i + 1 < input.length()) {
                        currentToken.append(input.charAt(i + 1));
                        i += 2;
                    } else {
                        currentToken.append(c);
                        i++;
                    }
                }
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                i++;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                i++;
            } else if (c == '>' && !inSingleQuote && !inDoubleQuote) {
                if (currentToken.length() > 0) {
                    String tokenStr = currentToken.toString();
                    if (tokenStr.equals("1") || tokenStr.equals("2")) {
                        String op = tokenStr;
                        if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                            op += ">>";
                            i += 2;
                        } else {
                            op += ">";
                            i += 1;
                        }
                        tokens.add(new Token(op, true));
                        currentToken = new StringBuilder();
                    } else {
                        tokens.add(new Token(tokenStr, false));
                        currentToken = new StringBuilder();
                        
                        String op = "";
                        if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                            op = ">>";
                            i += 2;
                        } else {
                            op = ">";
                            i += 1;
                        }
                        tokens.add(new Token(op, true));
                    }
                } else {
                    String op = "";
                    if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                        op = ">>";
                        i += 2;
                    } else {
                        op = ">";
                        i += 1;
                    }
                    tokens.add(new Token(op, true));
                }
                
                // Skip whitespace after redirection operator
                while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
                    i++;
                }
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (currentToken.length() > 0) {
                    tokens.add(new Token(currentToken.toString(), false));
                    currentToken = new StringBuilder();
                }
                while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
                    i++;
                }
            } else {
                currentToken.append(c);
                i++;
            }
        }
        
        if (currentToken.length() > 0) {
            tokens.add(new Token(currentToken.toString(), false));
        }
        
        CommandParseResult result = new CommandParseResult();
        for (int j = 0; j < tokens.size(); j++) {
            Token t = tokens.get(j);
            if (t.isRedirection) {
                if (j + 1 < tokens.size()) {
                    Token next = tokens.get(j + 1);
                    if (t.text.equals(">") || t.text.equals("1>")) {
                        result.stdoutFile = next.text;
                        result.stdoutAppend = false;
                    } else if (t.text.equals(">>") || t.text.equals("1>>")) {
                        result.stdoutFile = next.text;
                        result.stdoutAppend = true;
                    } else if (t.text.equals("2>")) {
                        result.stderrFile = next.text;
                        result.stderrAppend = false;
                    } else if (t.text.equals("2>>")) {
                        result.stderrFile = next.text;
                        result.stderrAppend = true;
                    }
                    j++; // skip the filename token
                }
            } else {
                result.args.add(t.text);
            }
        }
        
        return result;
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

    public static void executeCommand(CommandParseResult parseResult, List<String> commandList) {
        String command = parseResult.args.get(0);
        List<String> args = parseResult.args.subList(1, parseResult.args.size());

        // Save original stdout/stderr
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;

        java.io.FileOutputStream outStream = null;
        java.io.FileOutputStream errStream = null;

        try {
            // Setup stdout redirection if specified
            if (parseResult.stdoutFile != null) {
                File outFile = new File(parseResult.stdoutFile);
                if (outFile.getParentFile() != null && !outFile.getParentFile().exists()) {
                    outFile.getParentFile().mkdirs();
                }
                outStream = new java.io.FileOutputStream(outFile, parseResult.stdoutAppend);
                System.setOut(new java.io.PrintStream(outStream));
            }

            // Setup stderr redirection if specified
            if (parseResult.stderrFile != null) {
                File errFile = new File(parseResult.stderrFile);
                if (errFile.getParentFile() != null && !errFile.getParentFile().exists()) {
                    errFile.getParentFile().mkdirs();
                }
                errStream = new java.io.FileOutputStream(errFile, parseResult.stderrAppend);
                System.setErr(new java.io.PrintStream(errStream));
            }

            // Execute the command
            switch (command) {
                case "exit":
                    int exitCode = 0;
                    if (!args.isEmpty()) {
                        try {
                            exitCode = Integer.parseInt(args.get(0));
                        } catch (NumberFormatException e) {
                            // ignore or handle
                        }
                    }
                    System.exit(exitCode);
                    break;
                case "echo":
                    System.out.println(String.join(" ", args));
                    break;
                case "type":
                    if (!args.isEmpty()) {
                        type(commandList, args.get(0));
                    }
                    break;
                case "pwd":
                    System.out.println(System.getProperty("user.dir"));
                    break;
                case "cd":
                    String currentDir = System.getProperty("user.dir");
                    String target = args.isEmpty() ? "" : args.get(0);
                    String home = System.getenv("HOME");
                    if (home == null) {
                        home = System.getProperty("user.home");
                    }

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
                            System.out.println("cd: " + (args.isEmpty() ? "" : args.get(0)) + ": No such file or directory");
                        }
                    } catch (IOException e) {
                        if (file.exists() && file.isDirectory()) {
                            System.setProperty("user.dir", file.getAbsolutePath());
                        } else {
                            System.out.println("cd: " + (args.isEmpty() ? "" : args.get(0)) + ": No such file or directory");
                        }
                    }
                    break;
                case "jobs":
                    break;
                default:
                    executeExternal(parseResult);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace(originalErr);
        } finally {
            // Restore original streams
            System.setOut(originalOut);
            System.setErr(originalErr);

            // Close files
            if (outStream != null) {
                try { outStream.close(); } catch (IOException e) {}
            }
            if (errStream != null) {
                try { errStream.close(); } catch (IOException e) {}
            }
        }
    }

    public static void executeExternal(CommandParseResult parseResult) {
        List<String> args = parseResult.args;
        String command = args.get(0);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(args);

            if (parseResult.stdoutFile != null) {
                File outFile = new File(parseResult.stdoutFile);
                if (outFile.getParentFile() != null && !outFile.getParentFile().exists()) {
                    outFile.getParentFile().mkdirs();
                }
                if (parseResult.stdoutAppend) {
                    processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                } else {
                    processBuilder.redirectOutput(outFile);
                }
            } else {
                processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (parseResult.stderrFile != null) {
                File errFile = new File(parseResult.stderrFile);
                if (errFile.getParentFile() != null && !errFile.getParentFile().exists()) {
                    errFile.getParentFile().mkdirs();
                }
                if (parseResult.stderrAppend) {
                    processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                } else {
                    processBuilder.redirectError(errFile);
                }
            } else {
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = processBuilder.start();
            process.waitFor();
        } catch (IOException e) {
            System.out.println(command + ": command not found");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static List<String> getCompletionCandidates(String prefix) {
        List<String> candidates = new java.util.ArrayList<>();
        List<String> builtins = Arrays.asList("echo", "exit", "type", "pwd", "cd", "jobs");
        for (String builtin : builtins) {
            if (builtin.startsWith(prefix)) {
                candidates.add(builtin);
            }
        }
        
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(":");
            for (String dir : dirs) {
                File dirFile = new File(dir);
                if (dirFile.exists() && dirFile.isDirectory()) {
                    File[] files = dirFile.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile() && f.canExecute() && f.getName().startsWith(prefix)) {
                                if (!candidates.contains(f.getName())) {
                                    candidates.add(f.getName());
                                }
                            }
                        }
                    }
                }
            }
        }
        
        java.util.Collections.sort(candidates);
        return candidates;
    }

    public static List<String> getPathCompletionCandidates(String pathPrefix) {
        List<String> candidates = new java.util.ArrayList<>();
        
        String dirPath = ".";
        String filePrefix = pathPrefix;
        
        int lastSlash = pathPrefix.lastIndexOf('/');
        if (lastSlash != -1) {
            dirPath = pathPrefix.substring(0, lastSlash + 1);
            filePrefix = pathPrefix.substring(lastSlash + 1);
        }
        
        if (dirPath.isEmpty()) {
            dirPath = "/";
        }
        
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().startsWith(filePrefix)) {
                        String name = f.getName();
                        if (name.startsWith(".") && !filePrefix.startsWith(".")) {
                            continue;
                        }
                        if (f.isDirectory()) {
                            name += "/";
                        } else {
                            name += " ";
                        }
                        candidates.add(name);
                    }
                }
            }
        }
        
        java.util.Collections.sort(candidates);
        return candidates;
    }

    public static String findLongestCommonPrefix(List<String> strs) {
        if (strs == null || strs.isEmpty()) return "";
        String prefix = strs.get(0);
        for (int i = 1; i < strs.size(); i++) {
            while (strs.get(i).indexOf(prefix) != 0) {
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) return "";
            }
        }
        return prefix;
    }

    private static boolean isRawMode = false;
    private static final BufferedReader fallbackReader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));

    public static void disableRawMode() {
        if (isRawMode) {
            try {
                new ProcessBuilder("stty", "icanon", "echo")
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor();
            } catch (Exception e) {
                // ignore
            }
            isRawMode = false;
        }
    }

    public static boolean enableRawMode() {
        try {
            Process p = new ProcessBuilder("stty", "-icanon", "-echo")
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            int exitCode = p.waitFor();
            isRawMode = (exitCode == 0);
            return isRawMode;
        } catch (Exception e) {
            isRawMode = false;
            return false;
        }
    }

    public static String readRawLine() throws IOException {
        if (!enableRawMode()) {
            return fallbackReader.readLine();
        }
        StringBuilder buffer = new StringBuilder();
        boolean lastWasTab = false;
        
        try {
            while (true) {
                int b = System.in.read();
                if (b == -1 || b == 4) { // EOF or Ctrl+D
                    if (buffer.length() == 0) {
                        return null;
                    }
                    break;
                }
                
                if (b == 27) { // Escape sequence
                    int next = System.in.read();
                    if (next == 91) {
                        while (true) {
                            int code = System.in.read();
                            if (code >= 64 && code <= 126) {
                                break;
                            }
                        }
                    }
                    lastWasTab = false;
                    continue;
                }
                
                if (b == 127 || b == 8) { // Backspace
                    if (buffer.length() > 0) {
                        buffer.deleteCharAt(buffer.length() - 1);
                        System.out.print("\b \b");
                        System.out.flush();
                    }
                    lastWasTab = false;
                    continue;
                }
                
                if (b == '\n' || b == '\r') {
                    break;
                }
                
                if (b == 9) { // Tab
                    String currentInput = buffer.toString();
                    int lastSpace = currentInput.lastIndexOf(" ");
                    String lastWord = lastSpace == -1 ? currentInput : currentInput.substring(lastSpace + 1);
                    
                    List<String> candidates;
                    String filePrefix = "";
                    
                    if (lastSpace == -1) {
                        candidates = getCompletionCandidates(lastWord);
                    } else {
                        candidates = getPathCompletionCandidates(lastWord);
                        int lastSlash = lastWord.lastIndexOf('/');
                        if (lastSlash != -1) {
                            filePrefix = lastWord.substring(lastSlash + 1);
                        } else {
                            filePrefix = lastWord;
                        }
                    }
                    
                    if (candidates.size() == 1) {
                        String match = candidates.get(0);
                        String suffix;
                        if (lastSpace == -1) {
                            String completed = match + " ";
                            suffix = completed.substring(lastWord.length());
                        } else {
                            suffix = match.substring(filePrefix.length());
                        }
                        System.out.print(suffix);
                        System.out.flush();
                        buffer.append(suffix);
                    } else if (candidates.size() > 1) {
                        String lcp = findLongestCommonPrefix(candidates);
                        String currentPrefix = lastSpace == -1 ? lastWord : filePrefix;
                        if (lcp.length() > currentPrefix.length()) {
                            String suffix = lcp.substring(currentPrefix.length());
                            System.out.print(suffix);
                            System.out.flush();
                            buffer.append(suffix);
                        } else {
                            if (lastWasTab) {
                                System.out.print("\r\n");
                                System.out.print(String.join("  ", candidates) + "\r\n");
                                System.out.print("$ " + buffer.toString());
                                System.out.flush();
                            } else {
                                System.out.print("\u0007");
                                System.out.flush();
                            }
                        }
                    } else {
                        System.out.print("\u0007");
                        System.out.flush();
                    }
                    lastWasTab = true;
                    continue;
                }
                
                char c = (char) b;
                buffer.append(c);
                System.out.print(c);
                System.out.flush();
                lastWasTab = false;
            }
        } finally {
            disableRawMode();
        }
        
        System.out.println();
        return buffer.toString();
    }
}
