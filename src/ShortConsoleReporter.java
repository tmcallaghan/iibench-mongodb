import com.codahale.metrics.*;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A reporter which outputs measurements to a {@link PrintStream}, like {@code System.out}.
 */
public class ShortConsoleReporter extends ScheduledReporter {
    /**
     * Returns a new {@link Builder} for {@link ShortConsoleReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link ShortConsoleReporter}
     */
    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    /**
     * A builder for {@link ShortConsoleReporter} instances. Defaults to using the default locale and
     * time zone, writing to {@code System.out}, converting rates to events/second, converting
     * durations to milliseconds, printing headers every 24 output rows, and not filtering metrics.
     */
    public static class Builder {
        private final MetricRegistry registry;
        private PrintStream output;
        private Locale locale;
        private Clock clock;
        private TimeZone timeZone;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private int rowsBetweenHeaders;
        private MetricFilter filter;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.output = System.out;
            this.locale = Locale.getDefault();
            this.clock = Clock.defaultClock();
            this.timeZone = TimeZone.getDefault();
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.rowsBetweenHeaders = 24;
            this.filter = MetricFilter.ALL;
        }

        /**
         * Write to the given {@link PrintStream}.
         *
         * @param output a {@link PrintStream} instance.
         * @return {@code this}
         */
        public Builder outputTo(PrintStream output) {
            this.output = output;
            return this;
        }

        /**
         * Format numbers for the given {@link Locale}.
         *
         * @param locale a {@link Locale}
         * @return {@code this}
         */
        public Builder formattedFor(Locale locale) {
            this.locale = locale;
            return this;
        }

        /**
         * Use the given {@link Clock} instance for the time.
         *
         * @param clock a {@link Clock} instance
         * @return {@code this}
         */
        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Use the given {@link TimeZone} for the time.
         *
         * @param timeZone a {@link TimeZone}
         * @return {@code this}
         */
        public Builder formattedFor(TimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        /**
         * Convert rates to the given time unit.
         *
         * @param rateUnit a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Print a dstat-like header every n rows.
         *
         * @param n rows of data between header lines
         * @return {@code this}
         */
        public Builder setRowsBetweenHeaders(int rowsBetweenHeaders) {
            this.rowsBetweenHeaders = rowsBetweenHeaders;
            return this;
        }

        /**
         * Builds a {@link ShortConsoleReporter} with the given properties.
         *
         * @return a {@link ShortConsoleReporter}
         */
        public ShortConsoleReporter build() {
            return new ShortConsoleReporter(registry,
                                            output,
                                            locale,
                                            clock,
                                            timeZone,
                                            rateUnit,
                                            durationUnit,
                                            rowsBetweenHeaders,
                                            filter);
        }
    }

    private static final int CONSOLE_WIDTH = 80;

    private final PrintStream output;
    private final Locale locale;
    private final Clock clock;
    private final DateFormat dateFormat;
    private final int rowsBetweenHeaders;
    private int rowsSinceHeader;

    private ShortConsoleReporter(MetricRegistry registry,
                                 PrintStream output,
                                 Locale locale,
                                 Clock clock,
                                 TimeZone timeZone,
                                 TimeUnit rateUnit,
                                 TimeUnit durationUnit,
                                 int rowsBetweenHeaders,
                                 MetricFilter filter) {
        super(registry, "short-console-reporter", filter, rateUnit, durationUnit);
        this.output = output;
        this.locale = locale;
        this.clock = clock;
        this.dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                                                         DateFormat.MEDIUM,
                                                         locale);
        this.rowsBetweenHeaders = rowsBetweenHeaders;
        dateFormat.setTimeZone(timeZone);

        // Force header at beginning.
        rowsSinceHeader = rowsBetweenHeaders;
    }

    private void printHeader(String name, int width) {
        for (int i = 0; i < (width - name.length()) / 2; ++i) {
            output.print('-');
        }
        output.print(' ');
        output.print(name);
        output.print(' ');
        for (int i = 0; i < width - name.length() - ((width - name.length()) / 2); ++i) {
            output.print('-');
        }
    }

    private void printSubHeader(String name, int width) {
        for (int i = 0; i < width - name.length(); ++i) {
            output.print(' ');
        }
        output.print(name);
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, com.codahale.metrics.Timer> timers) {
        final String dateTime = dateFormat.format(new Date(clock.getTime()));

        if (rowsSinceHeader == rowsBetweenHeaders) {
            printHeader("time", dateTime.length());
            output.print(' ');

            for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                printHeader(entry.getKey(), 12);
                output.print(' ');
            }

            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                printHeader(entry.getKey(), 12);
                output.print(' ');
            }

            for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
                printHeader(entry.getKey(), 64);
                output.print(' ');
            }

            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                printHeader(entry.getKey(), 12);
                output.print(' ');
            }

            for (Map.Entry<String, com.codahale.metrics.Timer> entry : timers.entrySet()) {
                printHeader(entry.getKey(), 64);
                output.print(' ');
            }

            output.println();

            output.print(' ');
            printSubHeader("", dateTime.length());
            output.print(" |");

            for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                output.print(' ');
                printSubHeader("value", 12);
                output.print(" |");
            }

            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                output.print(' ');
                printSubHeader("count", 12);
                output.print(" |");
            }

            for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
                output.print(' ');
                printSubHeader("count", 12);
                output.print(' ');
                printSubHeader("mean", 12);
                output.print(' ');
                printSubHeader("median", 12);
                output.print(' ');
                printSubHeader("99", 12);
                output.print(' ');
                printSubHeader("99.9", 12);
                output.print(" |");
            }

            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                output.print(' ');
                printSubHeader("mean", 12);
                output.print(" |");
            }

            for (Map.Entry<String, com.codahale.metrics.Timer> entry : timers.entrySet()) {
                output.print(' ');
                printSubHeader("count", 12);
                output.print(' ');
                printSubHeader("mean", 12);
                output.print(' ');
                printSubHeader("median", 12);
                output.print(' ');
                printSubHeader("99", 12);
                output.print(' ');
                printSubHeader("99.9", 12);
                output.print(" |");
            }

            output.println();
            rowsSinceHeader = 0;
        }
        rowsSinceHeader++;

        output.print(' ');
        output.print(dateTime);
        output.print(" |");

        if (!gauges.isEmpty()) {
            for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                output.printf(locale, " %12s |", entry.getValue().getValue());
            }
        }

        if (!counters.isEmpty()) {
            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                output.printf(locale, " %12d |", entry.getValue().getCount());
            }
        }

        if (!histograms.isEmpty()) {
            for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
                Snapshot s = entry.getValue().getSnapshot();
                output.printf(locale, " %12d %12.3f %12.3f %12.3f %12.3f |",
                              entry.getValue().getCount(),
                              s.getMean(),
                              s.getMedian(),
                              s.get99thPercentile(),
                              s.get999thPercentile());
            }
        }

        if (!meters.isEmpty()) {
            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                output.printf(locale, " %12.3f |", convertRate(entry.getValue().getMeanRate()));
            }
        }

        if (!timers.isEmpty()) {
            for (Map.Entry<String, com.codahale.metrics.Timer> entry : timers.entrySet()) {
                Snapshot s = entry.getValue().getSnapshot();
                output.printf(locale, " %12d %12.3f %12.3f %12.3f %12.3f |",
                              entry.getValue().getCount(),
                              convertDuration(s.getMean()),
                              convertDuration(s.getMedian()),
                              convertDuration(s.get99thPercentile()),
                              convertDuration(s.get999thPercentile()));
            }
        }

        output.println();
        output.flush();
    }

    private void printMeter(Meter meter) {
        // output.printf(locale, "             count = %d%n", meter.getCount());
        // output.printf(locale, "         mean rate = %2.2f events/%s%n", convertRate(meter.getMeanRate()), getRateUnit());
        // output.printf(locale, "     1-minute rate = %2.2f events/%s%n", convertRate(meter.getOneMinuteRate()), getRateUnit());
        // output.printf(locale, "     5-minute rate = %2.2f events/%s%n", convertRate(meter.getFiveMinuteRate()), getRateUnit());
        // output.printf(locale, "    15-minute rate = %2.2f events/%s%n", convertRate(meter.getFifteenMinuteRate()), getRateUnit());
    }

    private void printCounter(Map.Entry<String, Counter> entry) {
        // output.printf(locale, "             count = %d%n", entry.getValue().getCount());
    }

    private void printGauge(Map.Entry<String, Gauge> entry) {
        // output.printf(locale, "             value = %s%n", entry.getValue().getValue());
    }

    private void printHistogram(Histogram histogram) {
        // output.printf(locale, "             count = %d%n", histogram.getCount());
        // Snapshot snapshot = histogram.getSnapshot();
        // output.printf(locale, "               min = %d%n", snapshot.getMin());
        // output.printf(locale, "               max = %d%n", snapshot.getMax());
        // output.printf(locale, "              mean = %2.2f%n", snapshot.getMean());
        // output.printf(locale, "            stddev = %2.2f%n", snapshot.getStdDev());
        // output.printf(locale, "            median = %2.2f%n", snapshot.getMedian());
        // output.printf(locale, "              75%% <= %2.2f%n", snapshot.get75thPercentile());
        // output.printf(locale, "              95%% <= %2.2f%n", snapshot.get95thPercentile());
        // output.printf(locale, "              98%% <= %2.2f%n", snapshot.get98thPercentile());
        // output.printf(locale, "              99%% <= %2.2f%n", snapshot.get99thPercentile());
        // output.printf(locale, "            99.9%% <= %2.2f%n", snapshot.get999thPercentile());
    }

    private void printTimer(com.codahale.metrics.Timer timer) {
        // final Snapshot snapshot = timer.getSnapshot();
        // output.printf(locale, "             count = %d%n", timer.getCount());
        // output.printf(locale, "         mean rate = %2.2f calls/%s%n", convertRate(timer.getMeanRate()), getRateUnit());
        // output.printf(locale, "     1-minute rate = %2.2f calls/%s%n", convertRate(timer.getOneMinuteRate()), getRateUnit());
        // output.printf(locale, "     5-minute rate = %2.2f calls/%s%n", convertRate(timer.getFiveMinuteRate()), getRateUnit());
        // output.printf(locale, "    15-minute rate = %2.2f calls/%s%n", convertRate(timer.getFifteenMinuteRate()), getRateUnit());

        // output.printf(locale, "               min = %2.2f %s%n", convertDuration(snapshot.getMin()), getDurationUnit());
        // output.printf(locale, "               max = %2.2f %s%n", convertDuration(snapshot.getMax()), getDurationUnit());
        // output.printf(locale, "              mean = %2.2f %s%n", convertDuration(snapshot.getMean()), getDurationUnit());
        // output.printf(locale, "            stddev = %2.2f %s%n", convertDuration(snapshot.getStdDev()), getDurationUnit());
        // output.printf(locale, "            median = %2.2f %s%n", convertDuration(snapshot.getMedian()), getDurationUnit());
        // output.printf(locale, "              75%% <= %2.2f %s%n", convertDuration(snapshot.get75thPercentile()), getDurationUnit());
        // output.printf(locale, "              95%% <= %2.2f %s%n", convertDuration(snapshot.get95thPercentile()), getDurationUnit());
        // output.printf(locale, "              98%% <= %2.2f %s%n", convertDuration(snapshot.get98thPercentile()), getDurationUnit());
        // output.printf(locale, "              99%% <= %2.2f %s%n", convertDuration(snapshot.get99thPercentile()), getDurationUnit());
        // output.printf(locale, "            99.9%% <= %2.2f %s%n", convertDuration(snapshot.get999thPercentile()), getDurationUnit());
    }

    private void printWithBanner(String s, char c) {
        // output.print(s);
        // output.print(' ');
        // for (int i = 0; i < (CONSOLE_WIDTH - s.length() - 1); i++) {
        //     output.print(c);
        // }
        // output.println();
    }
}
