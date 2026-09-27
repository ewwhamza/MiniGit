package minigit.cli;

import minigit.cli.commands.AddCommand;
import minigit.cli.commands.BranchCommand;
import minigit.cli.commands.CatFileCommand;
import minigit.cli.commands.CheckoutCommand;
import minigit.cli.commands.CommitCommand;
import minigit.cli.commands.ConfigCommand;
import minigit.cli.commands.DiffCommand;
import minigit.cli.commands.HashObjectCommand;
import minigit.cli.commands.HelpCommand;
import minigit.cli.commands.InitCommand;
import minigit.cli.commands.LogCommand;
import minigit.cli.commands.MergeCommand;
import minigit.cli.commands.ResetCommand;
import minigit.cli.commands.RestoreCommand;
import minigit.cli.commands.RmCommand;
import minigit.cli.commands.ShowCommand;
import minigit.cli.commands.StatusCommand;
import minigit.cli.commands.TagCommand;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Looks up commands by name. Commands are listed in help in registration order. */
public final class CommandRegistry {

    private final Map<String, Command> commands = new LinkedHashMap<>();

    /** A registry holding every built-in command. */
    public static CommandRegistry createDefault() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new InitCommand());
        registry.register(new AddCommand());
        registry.register(new RmCommand());
        registry.register(new CommitCommand());
        registry.register(new StatusCommand());
        registry.register(new DiffCommand());
        registry.register(new LogCommand());
        registry.register(new ShowCommand());
        registry.register(new BranchCommand());
        registry.register(new CheckoutCommand());
        registry.register(new MergeCommand());
        registry.register(new TagCommand());
        registry.register(new RestoreCommand());
        registry.register(new ResetCommand());
        registry.register(new HashObjectCommand());
        registry.register(new CatFileCommand());
        registry.register(new ConfigCommand());
        registry.register(new HelpCommand(registry));
        return registry;
    }

    public void register(Command command) {
        if (commands.putIfAbsent(command.name(), command) != null) {
            throw new IllegalArgumentException("duplicate command: " + command.name());
        }
    }

    public Optional<Command> find(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    public Collection<Command> all() {
        return Collections.unmodifiableCollection(commands.values());
    }
}
