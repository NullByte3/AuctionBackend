package club.nullbyte3.auction.impl;

import club.nullbyte3.auction.db.Item;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AuctionManager {
    @Getter
    private static Item currentItem;
    private static final Queue<Item> itemQueue = new ArrayDeque<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static volatile ScheduledFuture<?> endTask;
    private static final int AUCTION_DURATION = 15;
    private static BidManager bidManager;

    public static void start(BidManager bidManager) {
        AuctionManager.bidManager = bidManager;
        nextItem();
    }

    public static void addItem(Item item) {
        itemQueue.add(item);
    }

    public static void nextItem() {
        currentItem = itemQueue.poll();
        if (currentItem != null) {
            currentItem.setEndAt(LocalDateTime.now().plusSeconds(AUCTION_DURATION));
            bidManager.broadcastNewAuction();
        }
        resetTimer();
    }

    public static void resetTimer() {
        endTask = scheduler.schedule(AuctionManager::nextItem, AUCTION_DURATION, TimeUnit.SECONDS);
    }
}
