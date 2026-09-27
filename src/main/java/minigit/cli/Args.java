package minigit.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Splits a command's arguments into flags ({@code -w}), options with a value
 * ({@code -m "message"}), and positional arguments. {@code --} ends option
 * parsing, so a file named {@code -x} can be passed as {@code -- -x}.
 */
public final class Args {

    private final Set<String> flags = new HashSet<>();
    private final Map<String, String> options = new HashMap<>();
    private final List<String> positionals = new ArrayList<>();

    private Args() {
    }

    /**
     * @param knownFlags   options that take no value
     * @param knownOptions options followed by a value
     * @throws UsageException on an unknown option or a missing value
     */
    public static Args parse(List<String> raw, Set<String> knownFlags, Set<String> knownOptions) {
        Args args = new Args();
        boolean optionsEnded = false;
        for (int i = 0; i < raw.size(); i++) {
            String arg = raw.get(i);
            if (optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
                args.positionals.add(arg);
            } else if (arg.equals("--")) {
                optionsEnded = true;
            } else if (knownFlags.contains(arg)) {
                args.flags.add(arg);
            } else if (knownOptions.contains(arg)) {
                if (i + 1 >= raw.size()) {
                    throw new UsageException("option '" + arg + "' needs a value");
                }
                args.options.put(arg, raw.get(++i));
            } else {
                throw new UsageException("unknown option '" + arg + "'");
            }
        }
        return args;
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    public Optional<String> option(String name) {
        return Optional.ofNullable(options.get(name));
    }

    public List<String> positionals() {
        return positionals;
    }
}
