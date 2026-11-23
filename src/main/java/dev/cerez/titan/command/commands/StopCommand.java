package dev.cerez.titan.command.commands;

import dev.cerez.titan.command.Arguments;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.command.exception.IllegalCommandArgumentException;
import dev.cerez.titan.command.exception.ReferenceNotFoundException;
import dev.cerez.titan.core.Manager;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class StopCommand extends BaseCommand {

    public StopCommand(EnvironmentManager environmentManager) {
        super("stop", "sto", environmentManager);
    }

    @Override
    protected void execute(@NotNull Arguments args) {
        args.minLength(1);
        String managerString = args.get(0);
        Manager<?, ?> managerManager;
        if (Utils.isValidUUID(managerString)){
            UUID uuidManager = UUID.fromString(managerString);
            managerManager = this.environmentManager.getManager(uuidManager);
        }else managerManager = this.environmentManager.getManager(managerString);

        if (managerManager == null)
            throw new ReferenceNotFoundException("No encontró el manager con la referencia: %s".formatted(managerString));

        // No debería pasar, ya que el gestor de entorno no se registra a sí mismo
        if (managerManager == this.environmentManager)
            throw new IllegalCommandArgumentException("No se puede detener el gestor de entorno");

        managerManager.stop();
    }
}
