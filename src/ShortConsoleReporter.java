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

}
