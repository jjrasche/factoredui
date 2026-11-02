package ai.factoredui.compose.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun row(t: Number, vararg values: Pair<String, Number?>): Map<String, Any?> = mapOf("t" to t, "values" to values.toMap())

private fun areaDef(id: String, stack: String? = null) = mapOf("id" to id, "path" to "values.$id", "kind" to "area", "axis" to "left") + listOfNotNull(stack?.let { "stack" to it })

private val STACKED_ROWS = listOf(
    row(0, "a" to 2, "b" to 1, "c" to -1),
    row(1, "a" to 3, "b" to -2, "c" to 1),
    row(2, "a" to -1, "b" to -3, "c" to 2),
    row(3, "a" to 0, "b" to 4, "c" to -2),
)

private fun stackedProps(rows: List<Map<String, Any?>> = STACKED_ROWS) =
    mapOf("rows" to rows, "x" to "t", "series" to listOf(areaDef("a"), areaDef("b"), areaDef("c")), "axes" to mapOf("left" to mapOf("unit" to "W")))

private fun ChartPlot.band(id: String) = series.first { it.def.id == id }.runs.single().map { it.from to it.to }

class ChartPlotTest {
    @Test
    fun positive_values_stack_upward_from_zero_and_negative_values_downward_in_series_order() {
        val plot = plotChart(stackedProps())
        assertEquals(listOf(0.0 to 2.0, 0.0 to 3.0, 0.0 to -1.0, 0.0 to 0.0), plot.band("a"))
        assertEquals(listOf(2.0 to 3.0, 0.0 to -2.0, -1.0 to -4.0, 0.0 to 4.0), plot.band("b"))
        assertEquals(listOf(0.0 to -1.0, 3.0 to 4.0, 0.0 to 2.0, 0.0 to -2.0), plot.band("c"))
    }

    @Test
    fun the_stack_tops_and_bottoms_equal_the_sums_of_the_positive_and_the_negative_values_at_each_step() {
        val plot = plotChart(stackedProps())
        val tops = (0 until 4).map { step -> plot.series.maxOf { it.runs.single()[step].to } }
        val bottoms = (0 until 4).map { step -> plot.series.minOf { it.runs.single()[step].to } }
        assertEquals(listOf(3.0, 4.0, 2.0, 4.0), tops)
        assertEquals(listOf(-1.0, -2.0, -4.0, -2.0), bottoms)
    }

    @Test
    fun a_zero_between_two_negatives_stays_on_the_negative_edge_of_its_stack() {
        val rows = listOf(row(0, "a" to -2, "b" to -1), row(1, "a" to -2, "b" to 0.0), row(2, "a" to -2, "b" to -1))
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(areaDef("a"), areaDef("b"))))
        assertEquals(listOf(-2.0 to -3.0, -2.0 to -2.0, -2.0 to -3.0), plot.band("b"))
    }

    @Test
    fun a_zero_between_two_positives_stays_on_the_positive_edge_of_its_stack() {
        val rows = listOf(row(0, "a" to 2, "b" to 1), row(1, "a" to 2, "b" to 0.0), row(2, "a" to 2, "b" to 1))
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(areaDef("a"), areaDef("b"))))
        assertEquals(listOf(2.0 to 3.0, 2.0 to 2.0, 2.0 to 3.0), plot.band("b"))
    }

    @Test
    fun a_zero_at_the_start_takes_the_side_of_the_next_value_and_a_series_of_zeros_stays_above_zero() {
        val rows = listOf(row(0, "a" to -2, "b" to 0.0, "c" to 0.0), row(1, "a" to -2, "b" to -1, "c" to 0.0))
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(areaDef("a"), areaDef("b"), areaDef("c"))))
        assertEquals(listOf(-2.0 to -2.0, -2.0 to -3.0), plot.band("b"))
        assertEquals(listOf(0.0 to 0.0, 0.0 to 0.0), plot.band("c"))
    }

    @Test
    fun a_zero_does_not_change_the_value_the_readout_gives() {
        val rows = listOf(row(0, "a" to -2), row(1, "a" to 0.0), row(2, "a" to -2))
        val readout = chartReadout(plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(areaDef("a")))), 1.0)!!
        assertEquals(0.0, readout.entries.single().value)
    }

    @Test
    fun an_area_that_changes_sign_is_drawn_as_two_pieces_meeting_at_the_crossing() {
        val rows = listOf(row(0, "a" to 2), row(1, "a" to 4), row(2, "a" to -4), row(3, "a" to -2))
        val run = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(areaDef("a")))).series.single().runs.single()
        val pieces = chartAreaPieces(run)
        assertEquals(2, pieces.size)
        assertEquals(listOf(0.0, 1.0, 1.5), pieces[0].map { it.x })
        assertEquals(listOf(1.5, 2.0, 3.0), pieces[1].map { it.x })
        assertEquals(0.0, pieces[0].last().value)
        assertEquals(0.0, pieces[1].first().value)
    }

    @Test
    fun the_crossing_sits_where_the_straight_line_between_the_two_values_meets_zero() {
        val rows = listOf(row(0, "a" to 1), row(10, "a" to -3))
        val run = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(areaDef("a")))).series.single().runs.single()
        assertEquals(2.5, chartAreaPieces(run).first().last().x)
    }

    @Test
    fun an_area_that_keeps_its_sign_through_a_zero_is_one_piece() {
        val rows = listOf(row(0, "a" to -2), row(1, "a" to 0.0), row(2, "a" to -2))
        val run = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(areaDef("a")))).series.single().runs.single()
        assertEquals(listOf(run), chartAreaPieces(run))
    }

    @Test
    fun the_x_axis_has_a_tick_at_each_multiple_of_its_step_inside_the_rows_with_the_label_the_axis_asks_for() {
        val rows = (2757..2780).map { row(it, "a" to it) }
        val props = mapOf("rows" to rows, "x" to "t", "x_axis" to mapOf("every" to 3, "as" to "hour_of_day"), "series" to listOf(mapOf("id" to "a", "path" to "values.a")))
        val ticks = chartXTicks(plotChart(props))
        assertEquals(listOf(2757.0, 2760.0, 2763.0, 2766.0, 2769.0, 2772.0, 2775.0, 2778.0), ticks.map { it.x })
        assertEquals(listOf("21:00", "00:00", "03:00", "06:00", "09:00", "12:00", "15:00", "18:00"), ticks.map { it.label })
    }

    @Test
    fun a_number_axis_labels_its_ticks_with_the_figure_and_no_axis_gives_no_ticks() {
        val rows = (0..6).map { row(it, "a" to it) }
        val series = listOf(mapOf("id" to "a", "path" to "values.a"))
        assertEquals(listOf("0", "2", "4", "6"), chartXTicks(plotChart(mapOf("rows" to rows, "x" to "t", "x_axis" to mapOf("every" to 2), "series" to series))).map { it.label })
        assertEquals(emptyList(), chartXTicks(plotChart(mapOf("rows" to rows, "x" to "t", "series" to series))))
    }

    @Test
    fun a_tick_label_kind_it_does_not_know_falls_back_to_numbers_and_is_named() {
        val rows = (0..4).map { row(it, "a" to it) }
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "x_axis" to mapOf("every" to 2, "as" to "roman"), "series" to listOf(mapOf("id" to "a", "path" to "values.a"))))
        assertEquals(listOf("0", "2", "4"), chartXTicks(plot).map { it.label })
        assertEquals(listOf("chart x_axis: as must be number or hour_of_day, not 'roman'; ticks are labelled as numbers."), plot.notices)
    }

    @Test
    fun a_tick_step_that_is_not_a_positive_number_gives_no_ticks_and_a_notice() {
        val rows = (0..6).map { row(it, "a" to it) }
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "x_axis" to mapOf("every" to 0), "series" to listOf(mapOf("id" to "a", "path" to "values.a"))))
        assertEquals(emptyList(), chartXTicks(plot))
        assertEquals(listOf("chart x_axis: every must be a number above zero; no ticks are drawn."), plot.notices)
    }

    @Test
    fun areas_with_different_stack_names_do_not_stack_on_each_other() {
        val props = stackedProps() + ("series" to listOf(areaDef("a", "first"), areaDef("b", "second")))
        val plot = plotChart(props)
        assertEquals(listOf(0.0 to 1.0, 0.0 to -2.0, 0.0 to -3.0, 0.0 to 4.0), plot.band("b"))
    }

    @Test
    fun a_line_is_its_values_at_the_x_of_each_row_read_by_a_dotted_path() {
        val rows = (0 until 3).map { mapOf("t" to it * 2, "stocks" to mapOf("water_f" to 50 + it * 5)) }
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(mapOf("id" to "water", "path" to "stocks.water_f", "kind" to "line", "axis" to "right"))))
        val points = plot.series.single().runs.single()
        assertEquals(listOf(0.0 to 50.0, 2.0 to 55.0, 4.0 to 60.0), points.map { it.x to it.value })
        assertEquals(ChartSide.RIGHT, plot.series.single().def.side)
    }

    @Test
    fun a_row_with_no_number_at_the_path_breaks_the_line_into_two_runs() {
        val rows = listOf(row(0, "a" to 1), row(1, "a" to null), row(2, "a" to 3), row(3, "a" to 4))
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(mapOf("id" to "a", "path" to "values.a", "kind" to "line"))))
        assertEquals(listOf(1, 2), plot.series.single().runs.map { it.size })
    }

    @Test
    fun the_window_keeps_only_the_rows_whose_x_is_inside_it_ends_included() {
        val rows = (0 until 10).map { row(it, "a" to it) }
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "window" to mapOf("from" to 3, "to" to 5), "series" to listOf(mapOf("id" to "a", "path" to "values.a", "kind" to "line"))))
        assertEquals(listOf(3.0, 4.0, 5.0), plot.series.single().runs.single().map { it.x })
    }

    @Test
    fun a_window_may_give_its_start_and_its_length_instead_of_its_end() {
        val rows = (0 until 10).map { row(it, "a" to it) }
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "window" to mapOf("from" to 3, "length" to 2), "series" to listOf(mapOf("id" to "a", "path" to "values.a"))))
        assertEquals(listOf(3.0, 4.0, 5.0), plot.series.single().runs.single().map { it.x })
    }

    @Test
    fun a_window_whose_start_is_not_a_number_yet_leaves_every_row_in() {
        val rows = (0 until 4).map { row(it, "a" to it) }
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "window" to mapOf("from" to null, "length" to 1), "series" to listOf(mapOf("id" to "a", "path" to "values.a"))))
        assertEquals(4, plot.series.single().runs.single().size)
    }

    @Test
    fun a_series_that_cannot_be_drawn_is_dropped_and_named() {
        val plot = plotChart(
            mapOf(
                "rows" to listOf(row(0, "a" to 1)), "x" to "t",
                "series" to listOf(
                    mapOf("id" to "odd", "path" to "values.a", "kind" to "bars"),
                    mapOf("id" to "absent", "path" to "values.zzz", "kind" to "line"),
                    mapOf("path" to "values.a", "kind" to "line"),
                ),
            ),
        )
        assertEquals(emptyList(), plot.series.map { it.def.id })
        assertEquals(
            listOf(
                "chart series 'odd': kind must be line or area, not 'bars'; it is not drawn.",
                "chart series 'absent': no row has a number at 'values.zzz'; it is not drawn.",
                "chart series 2: it needs an id and a path; it is not drawn.",
            ),
            plot.notices,
        )
    }

    @Test
    fun a_series_on_an_axis_that_is_neither_left_nor_right_is_dropped_and_named() {
        val plot = plotChart(mapOf("rows" to listOf(row(0, "a" to 1)), "x" to "t", "series" to listOf(mapOf("id" to "a", "path" to "values.a", "axis" to "top"))))
        assertEquals(emptyList(), plot.series)
        assertEquals(listOf("chart series 'a': axis must be left or right, not 'top'; it is not drawn."), plot.notices)
    }

    @Test
    fun rows_with_no_number_at_x_are_left_out_and_counted() {
        val rows = listOf(row(0, "a" to 1), mapOf("t" to "soon", "values" to mapOf("a" to 2)), row(2, "a" to 3))
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(mapOf("id" to "a", "path" to "values.a"))))
        assertEquals(listOf(0.0, 2.0), plot.series.single().runs.single().map { it.x })
        assertEquals(listOf("chart: 1 row has no number at 't'; it is not drawn."), plot.notices)
    }

    @Test
    fun a_chart_with_no_rows_or_no_x_path_draws_nothing_and_says_why() {
        assertEquals(listOf("chart: rows must be a list of objects."), plotChart(mapOf("x" to "t")).notices)
        assertEquals(listOf("chart: x must name the path of each row's x value."), plotChart(mapOf("rows" to emptyList<Any>())).notices)
    }

    @Test
    fun an_axis_spans_the_values_on_its_side_and_always_includes_zero_when_it_holds_an_area() {
        val plot = plotChart(stackedProps())
        val range = chartRange(plot, ChartSide.LEFT)
        assertNotNull(range)
        assertEquals(-4.0, range.ticks.first())
        assertEquals(4.0, range.ticks.last())
        assertNull(chartRange(plot, ChartSide.RIGHT))
    }

    @Test
    fun the_ticks_of_a_line_axis_are_round_numbers_around_its_values() {
        val rows = listOf(row(0, "a" to 48.2), row(1, "a" to 87.9))
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(mapOf("id" to "a", "path" to "values.a", "kind" to "line"))))
        assertEquals(listOf(40.0, 50.0, 60.0, 70.0, 80.0, 90.0), chartRange(plot, ChartSide.LEFT)!!.ticks)
    }

    @Test
    fun a_flat_line_still_gets_an_axis_with_a_span() {
        val rows = listOf(row(0, "a" to 5), row(1, "a" to 5))
        val plot = plotChart(mapOf("rows" to rows, "x" to "t", "series" to listOf(mapOf("id" to "a", "path" to "values.a", "kind" to "line"))))
        val range = chartRange(plot, ChartSide.LEFT)!!
        assertEquals(true, range.ticks.first() < 5.0 && range.ticks.last() > 5.0)
    }

    @Test
    fun the_readout_at_an_x_is_the_nearest_row_with_each_series_own_value_not_its_stacked_edge() {
        val readout = chartReadout(plotChart(stackedProps()), 1.4)!!
        assertEquals(1.0, readout.x)
        assertEquals(listOf("a" to 3.0, "b" to -2.0, "c" to 1.0), readout.entries.map { it.id to it.value })
    }

    @Test
    fun a_readout_names_the_axis_unit_of_each_value() {
        val readout = chartReadout(plotChart(stackedProps()), 0.0)!!
        assertEquals("2 W", readout.entries.first().text)
    }

    @Test
    fun values_are_shown_with_at_most_two_decimals() {
        assertEquals("2", formatChartValue(2.0))
        assertEquals("2.35", formatChartValue(2.3456))
        assertEquals("-0.5", formatChartValue(-0.5))
        assertEquals("1200", formatChartValue(1200.0))
    }

    private fun legendOf(extra: Map<String, Any?> = emptyMap()): List<ChartLegendEntry> {
        val series = listOf(
            mapOf("id" to "sun", "label" to "Sun", "path" to "a", "kind" to "area", "stack" to "gains"),
            mapOf("id" to "heat", "label" to "Heat into air", "path" to "b", "kind" to "area", "stack" to "gains"),
            mapOf("id" to "glass", "label" to "Glazing", "path" to "c", "kind" to "area", "stack" to "losses"),
            mapOf("id" to "air", "label" to "Air", "path" to "d", "kind" to "line"),
        )
        val rows = listOf(mapOf("t" to 0.0, "a" to 1.0, "b" to 2.0, "c" to -1.0, "d" to 60.0), mapOf("t" to 1.0, "a" to 2.0, "b" to 1.0, "c" to -2.0, "d" to 61.0))
        return chartLegend(plotChart(mapOf("rows" to rows, "x" to "t", "series" to series) + extra))
    }

    @Test
    fun with_no_stack_labels_the_legend_has_an_entry_for_each_series_in_order() {
        assertEquals(listOf("Sun", "Heat into air", "Glazing", "Air"), legendOf().map { it.label })
    }

    @Test
    fun a_labelled_stack_is_one_legend_entry_standing_for_all_its_series() {
        val legend = legendOf(mapOf("stack_labels" to mapOf("gains" to "Heat in", "losses" to "Heat out")))
        assertEquals(listOf("Heat in", "Heat out", "Air"), legend.map { it.label })
        assertEquals(listOf(listOf("sun", "heat"), listOf("glass"), listOf("air")), legend.map { it.seriesIds })
    }

    @Test
    fun a_stack_with_no_label_keeps_an_entry_for_each_of_its_series() {
        assertEquals(listOf("Heat in", "Glazing", "Air"), legendOf(mapOf("stack_labels" to mapOf("gains" to "Heat in"))).map { it.label })
    }
    private val LABELLED_ROWS = listOf(
        mapOf("t" to 2757.0, "at" to "Tue 3 Nov, 21:00", "v" to 1.0),
        mapOf("t" to 2758.0, "at" to "Tue 3 Nov, 22:00", "v" to 2.0),
        mapOf("t" to 2781.0, "at" to "Wed 4 Nov, 21:00", "v" to 3.0),
    )

    @Test
    fun the_window_label_is_the_label_of_the_first_row_in_the_window() {
        val props = mapOf("rows" to LABELLED_ROWS, "x" to "t", "row_label" to "at", "window" to mapOf("from" to 2758.0, "length" to 23.0))
        assertEquals("Tue 3 Nov, 22:00", chartWindowLabel(props))
    }

    @Test
    fun with_no_window_the_label_is_the_first_rows() {
        assertEquals("Tue 3 Nov, 21:00", chartWindowLabel(mapOf("rows" to LABELLED_ROWS, "x" to "t", "row_label" to "at")))
    }

    @Test
    fun a_window_with_no_rows_or_rows_with_no_label_have_no_window_label() {
        assertEquals(null, chartWindowLabel(mapOf("rows" to LABELLED_ROWS, "x" to "t", "row_label" to "at", "window" to mapOf("from" to 9000.0, "length" to 5.0))))
        assertEquals(null, chartWindowLabel(mapOf("rows" to LABELLED_ROWS, "x" to "t")))
        assertEquals(null, chartWindowLabel(mapOf("rows" to LABELLED_ROWS, "x" to "t", "row_label" to "nowhere")))
    }}
