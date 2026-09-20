package dev.cerez.titan.command.commands;

import dev.cerez.titan.Log;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.strategy.fuding.FundingManager;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class CheckFundingCommand extends BaseCommand {

    public CheckFundingCommand() {
        super("check");
    }

    @Override
    public void execute(@NotNull List<String> args) {
        if (args.isEmpty()) {
            Log.error("No arguments supplied");
            return;
        }

        if (args.size() < 2) {
            return;
        }
        FundingManager.FundingManagerConfig config = FundingManager.FundingManagerConfig.builder()
                .sizePosition(new BigDecimal(12))
                .booking(new BigDecimal("0.1"))
                .baseAsset("ONG")
                .quoteAsset("USDT")
                .logsEndPoints(true)
                .build();
        String baseAsset = args.get(0);
        String quotAsset = args.get(1);
        Log.info("Checking funding for symbol: " + baseAsset + quotAsset);

    }
}
