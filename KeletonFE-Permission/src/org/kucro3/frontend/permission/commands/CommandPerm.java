package org.kucro3.frontend.permission.commands;

import org.kucro3.frontend.permission.I18n;
import org.kucro3.frontend.permission.misc.Misc;
import org.kucro3.keleton.i18n.LocaleProperties;
import org.kucro3.keleton.permission.EnhancedPermissionService;
import org.kucro3.keleton.text.TextUtil;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectData;

import java.util.*;
import java.util.function.Function;

public class CommandPerm implements CommandExecutor {
    public CommandPerm(EnhancedPermissionService service, LocaleProperties locale)
    {
        this.service = service;
        this.locale = locale;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException
    {
        final String operation = args.<String>getOne("operation").get();
        final String type = args.<String>getOne("type").get();
        final String permission = args.<String>getOne("argument").get();
        final String argument = args.<String>getOne("argument").get();
        final String targ = args.<String>getOne("target").get();

        final Subject subject;

        final CommandResult.Builder commandResult = CommandResult.builder();

        StringBuilder perm = new StringBuilder("permission.perm");
        Function<Void, Boolean> transaction;

        Optional<Subject> _optional = Misc.parseSubjectWithMessage(
                src,
                service,
                locale,
                type,
                targ
        );

        if(!_optional.isPresent())
            return CommandResult.empty();

        subject = _optional.get();

        final SubjectData data = subject.getSubjectData();
        perm.append(".").append(type);

        Optional<Function<Void, Boolean>> optional =
                Misc.functionOrdinaryPermissionOperation(
                        src,
                        locale,
                        operation,
                        subject,
                        permission,
                        commandResult
                );

        if(optional.isPresent())
            transaction = optional.get();
        else
            switch(operation)
            {
                case "include":
                    transaction = (unused) -> {
                        if(!service.getGroupSubjects().hasRegistered(argument))
                            src.sendMessage(TextUtil.fromColored(locale.by(I18n.LOCALE_NO_SUCH_GROUP, argument)));
                        else
                        {
                            Subject parent = service.getGroupSubjects().get(argument);
                            if(parent.isChildOf(subject))
                                src.sendMessage(TextUtil.fromColored(locale.by(I18n.LOCALE_INHERITANCE_LOOP, argument)));
                            else if(subject.isChildOf(parent))
                                src.sendMessage(TextUtil.fromColored(locale.by(I18n.LOCALE_IHERITANCE_EXISTS, argument)));
                            else
                                return data.addParent(subject.getActiveContexts(), parent);
                        }
                        return null;
                    };
                    break;

                case "exclude":
                    transaction = (unused) -> {
                        if(!service.getGroupSubjects().hasRegistered(argument))
                            src.sendMessage(TextUtil.fromColored(locale.by(I18n.LOCALE_NO_SUCH_GROUP, argument)));
                        else
                        {
                            Subject parent = service.getGroupSubjects().get(argument);
                            if(!subject.isChildOf(parent))
                                src.sendMessage(TextUtil.fromColored(locale.by(I18n.LOCALE_INHERITANCE_NOT_EXISTS, argument)));
                            else
                                return data.removeParent(subject.getActiveContexts(), parent);
                        }
                        return null;
                    };
                    break;

                default:
                    src.sendMessage(TextUtil.fromColored(locale.by(I18n.LOCALE_UNKNOWN_OPERATION, operation)));
                    return CommandResult.empty();
            }

        perm.append(".").append(operation);

        if(!Misc.checkPermission(src, locale, perm.toString()))
            return CommandResult.empty();

        Misc.computeResultWithMessage(
                src,
                locale,
                commandResult,
                transaction.apply(null)
        );

        return commandResult.build();
    }

    private final EnhancedPermissionService service;

    private final LocaleProperties locale;
}
