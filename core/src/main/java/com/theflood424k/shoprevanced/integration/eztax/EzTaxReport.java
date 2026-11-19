package com.theflood424k.shoprevanced.integration.eztax;

import com.theflood424k.shoprevanced.shop.AbstractShop;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collects tax data for ezTax filing and remittance reporting.
 *
 * <p>Records each taxable transaction so an exportable report can be generated
 * for tax filing. All mutable state uses concurrent collections so the report
 * can be written to from region-owning scheduler threads without external
 * synchronization.
 */
public class EzTaxReport {

    /** A single taxable transaction record. */
    public static final class TaxRecord {
        public final Instant timestamp;
        public final String playerUuid;
        public final String playerName;
        public final String shopOwnerUuid;
        public final String shopLocation;
        public final double basePrice;
        public final double taxAmount;
        public final double totalPrice;

        public TaxRecord(Player player, AbstractShop shop,
                         double basePrice, double taxAmount, double totalPrice) {
            this.timestamp = Instant.now();
            this.playerUuid = player != null ? player.getUniqueId().toString() : null;
            this.playerName = player != null ? player.getName() : null;
            this.shopOwnerUuid = shop != null ? shop.getOwnerUUID().toString() : null;
            this.shopLocation = shop != null ? shop.getSignLocation().toString() : null;
            this.basePrice = basePrice;
            this.taxAmount = taxAmount;
            this.totalPrice = totalPrice;
        }
    }

    private final List<TaxRecord> records = new ArrayList<>();
    private final ConcurrentMap<String, Double> taxByWorld = new ConcurrentHashMap<>();
    private final AtomicLong transactionCount = new AtomicLong(0);
    private final AtomicReference<Double> totalTaxCollected = new AtomicReference<>(0.0);

    /** Records a taxable transaction. No-op when {@code taxAmount} is zero. */
    public void recordTransaction(Player player, AbstractShop shop,
                                  double basePrice, double taxAmount, double totalPrice) {
        if (taxAmount <= 0.0) {
            return;
        }
        TaxRecord record = new TaxRecord(player, shop, basePrice, taxAmount, totalPrice);
        synchronized (records) {
            records.add(record);
        }
        if (player != null) {
            taxByWorld.merge(player.getWorld().getName(), taxAmount, Double::sum);
        }
        transactionCount.incrementAndGet();
        totalTaxCollected.updateAndGet(current -> current + taxAmount);
    }

    /** @return an immutable copy of all recorded tax transactions. */
    public List<TaxRecord> getRecords() {
        synchronized (records) {
            return Collections.unmodifiableList(new ArrayList<>(records));
        }
    }

    /** @return number of taxable transactions recorded. */
    public long getTransactionCount() {
        return transactionCount.get();
    }

    /** @return total tax collected across all transactions. */
    public double getTotalTaxCollected() {
        return totalTaxCollected.get();
    }

    /** @return tax collected grouped by customer world. */
    public Map<String, Double> getTaxByWorld() {
        return Collections.unmodifiableMap(new java.util.HashMap<>(taxByWorld));
    }

    /**
     * Generates a plain-text report suitable for ezTax filing/remittance.
     *
     * @return a formatted multi-line report.
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("ezTax Sales Tax Report").append(System.lineSeparator());
        sb.append("Generated: ").append(Instant.now()).append(System.lineSeparator());
        sb.append("----------------------------------------").append(System.lineSeparator());
        sb.append("Total taxable transactions: ").append(getTransactionCount()).append(System.lineSeparator());
        sb.append("Total tax collected: ").append(String.format("%.2f", getTotalTaxCollected())).append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("Tax by customer location:").append(System.lineSeparator());
        getTaxByWorld().forEach((world, tax) ->
                sb.append("  ").append(world).append(": ").append(String.format("%.2f", tax)).append(System.lineSeparator()));
        sb.append(System.lineSeparator());
        sb.append("Transaction detail:").append(System.lineSeparator());
        for (TaxRecord record : getRecords()) {
            sb.append("  ").append(record.timestamp).append(" | ")
                    .append(record.playerName).append(" (").append(record.playerUuid).append(") | ")
                    .append("shop=").append(record.shopLocation).append(" | ")
                    .append("base=").append(String.format("%.2f", record.basePrice)).append(" | ")
                    .append("tax=").append(String.format("%.2f", record.taxAmount)).append(" | ")
                    .append("total=").append(String.format("%.2f", record.totalPrice))
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

    /** Clears all recorded tax data (e.g. after a filing period is closed). */
    public void clear() {
        synchronized (records) {
            records.clear();
        }
        taxByWorld.clear();
        transactionCount.set(0);
        totalTaxCollected.set(0.0);
    }
}
