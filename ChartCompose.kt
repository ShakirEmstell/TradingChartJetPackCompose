package me.emstell.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import kotlinx.coroutines.*
import shakir.bhav.common.*
import java.util.*
import kotlin.math.absoluteValue

var t = arrayListOf<Long>() //time stamp in seconds
    set(value) {
        field = value
        duration = t.mapIndexed { index, l -> l - (t.getOrNull(index - 1) ?: 0) }.minOfOrNull { it }
    }

var duration: Long? = null


class ChartTheme() {
    var gridLineColor = Color(0xff3C3F41)
    var valueLineColor = Color.Blue
    var horizontalLineColor = Color.Red
    var greenCandleColor = Color(0xff26A59A)
    var redCandleColor = Color(0xffEF5350)
    var crossHairColor = Color(0xFFC1E1AD)
}

val chartTheme = ChartTheme().apply {
//    gridLineColor = Color.Transparent
//    valueLineColor = Color.Transparent
//    horizontalLineColor = Color.Transparent
//    greenCandleColor = Color(0x11111111)
//    redCandleColor = Color(0x11111111)
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChartCompose() {


    var priceLineZoomStartedY = -1.0f
    var zoomMax = -1.0f
    var priceLineZoomStartedTop = -1.0f
    var priceLineZoomStartedBottom = -1.0f
    var isPriceZooming = false


    var dragStartedX = -1.0f
    var dragStartedY = -1.0f
    var dragStartedPriceTop = -1.0f
    var dragStartedPriceBottom = -1.0f
    var dragStartedStartTime = 0L
    var dragStartedEndTime = 0L
    var isDragging = false


    var crossHairVisible = false
    var crossHairX = -1.0f
    var crossHairY = -1.0f
    var crossHairPressX = -1.0f
    var crossHairPressY = -1.0f
    var crossHairXWhenPress = -1.0f
    var crossHairYWhenPress = -1.0f
    var crossHairClickedPrice: Float? = null
    val crossHairLongPressThreshold = 300L
    var crossHairIsWaiting = false


    var pinchZoomStarted = false
    var pinchX0: Float? = null
    var pinchY0: Float? = null
    var pinchX1: Float? = null
    var pinchY1: Float? = null
    var finger0ID = -1L
    var finger1ID = -1L


    var pinchStartedDistance: Float? = null
    var pinchStartedTimeStart = 0L
    var pinchStartedTimeEnd = 0L
    val pinchZoomIdArrayList = arrayListOf<Long>()


    var drawScope: DrawScope? = null


    var priceBottom = 100.0f
    var priceTop = 200.0f
    var timeStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis.div(1000)
    var timeEnd = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis.div(1000)


    var duration: Long? = null

    var h = mutableListOf<Float>()
    var l = mutableListOf<Float>()
    var c = mutableListOf<Float>()
    var o = mutableListOf<Float>()
    var valueLines = arrayListOf<Pair<String, ArrayList<Float>>>()
    var orderLines = arrayListOf<Triple<String, Float, Color>>()
    var notificationLines = arrayListOf<Float>()
    var notificationLinesIndexToBeDeleted: Int = -1
    var onNotificationListChangedCallBack: ((List<Float>) -> Unit)? = null

    var horizontalLines = arrayListOf<Tuple4<String, Float, Long, Long>>()


    var canvasWidth: Float = 0.0f
    var canvasHeight: Float = 0.0f
    var priceLineWidth: Float = 100.0f
    var priceLineTextLength = 0
    var priceLineX: Float = 0.0f

    val timeLineHeight = 20.0f //sdp
    val crossHairTextHeight = 25.0f //sdp
    val crossHairTimeWidth = 100.0f //sdp
    var timeLineY: Float = 0.0f

    var coordinatePriceRatio: Float = 0.0f //todo min max
    var coordinateTimeRatio: Float = 0.0f //todo min max


    var TICK_MULTIPLER = 100000.0f
    var TICK_DIVISER = 1L
    var DECIMAL_POINTS = 2

    fun gestureStatusReset_DESKTOP(){
        pinchZoomStarted=false
        isDragging=false
        crossHairVisible=false
        isPriceZooming=false
    }


    fun roundToTickPrice(price: Float, floor: Boolean = true): Float {
        var intttt = price.times(TICK_MULTIPLER).toLong()
        while (true) {
            if (intttt % TICK_DIVISER == 0L)
                return intttt.div(TICK_MULTIPLER)
            if (floor)
                intttt--
            else
                intttt++
        }
        return intttt.div(TICK_MULTIPLER)
    }


    fun priceToCoordinate(price: Float): Float {
        return (canvasHeight - ((price - priceBottom) * coordinatePriceRatio))
    }

    fun coordinateToPrice(y: Float): Float {
        return ((canvasHeight - y) / coordinatePriceRatio) + priceBottom
    }

    fun timeToCoordinate(time: Long): Float {
        return (((timeStart - time) * coordinateTimeRatio))
    }


    fun coordinateTotime(x: Float): Long {
        return (timeStart) - (x / coordinateTimeRatio).toLong()
    }


    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Color) {
        drawScope?.drawLine(
            start = Offset(x = x1, y = y1),
            end = Offset(x = x2, y = y2),
            color = color
        )
    }


    val crossHairPath = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    fun drawLineDotted(x0: Float, y0: Float, x1: Float, y1: Float, color: Color) {

        drawScope?.drawLine(
            start = Offset(x = x0, y = y0),
            end = Offset(x = x1, y = y1),
            color = color,
            pathEffect = crossHairPath
        )


    }


    fun drawText(textString: String, d: Float, coordinate: Float, s: String, align: String = "") {
//todo

    }


    fun calculatePriceLineWidth() {
        var max = coordinateToPrice(0.0f).printFloat(DECIMAL_POINTS)
        if (priceLineTextLength != max.length) {
            priceLineTextLength = max.length
            //  val rect = Rect()
            //   priceTextPaint.getTextBounds(max, 0, max.length, rect)
            //  priceLineWidth = priceTextPaint.measureText(max) .minus((priceTextPaint.ascent() * 2))
            //todo
            //postInvalidate()
        }

    }


    fun drawPriceGridAndTexts(pUnit: Float) {
        var i = priceBottom
        while (i <= priceTop) {
            val coordinate = priceToCoordinate(i)
            drawLine(0.0f, coordinate, canvasWidth - priceLineWidth, coordinate, chartTheme.gridLineColor)
            //     val rect = Rect()
            val textString = i.printFloat(DECIMAL_POINTS)
            //  priceTextPaint.getTextBounds(textString, 0, textString.length, rect)
            drawText(
                textString,
                priceLineX + 5,
                coordinate,
                "#C1E1AD"
            )
            i += pUnit
        }
    }


    fun drawTimeGridsAndTexts(tIndexUnit: Long) {
        // println("ChartView drawTimeGridsAndTexts")
        var k = timeStart
        while (k <= timeEnd) {
            if (k >= t.first() && k <= t.last()) {

                val coordinate = timeToCoordinate(k)

                drawLine(coordinate, 0.0f, coordinate, canvasHeight - timeLineHeight, chartTheme.gridLineColor)
                // val rect = Rect()
                val textString = milliToHM(k.times(1000)).toString()
                // timeTextPaint.getTextBounds(textString, 0, textString.length, rect)
                drawText(
                    textString,
                    coordinate,
                    canvasHeight - timeLineHeight /*- (timeTextPaint.ascent())*/ + (timeLineHeight / 3),
                    "#C1E1AD"
                )

            }
            k += tIndexUnit

        }
    }

    fun drawLinesOnValues(points: ArrayList<Float>) {
        // println("ChartView drawLinesOnValues")
        var prevX: Float? = null
        var prevY: Float? = null
        points.forEachIndexed { index, close ->
            val x = timeToCoordinate(t[index])
            val y = priceToCoordinate(close)
            if (prevX != null && prevY != null) {
                if (y > prevY!!)
                    drawLine(prevX!!, prevY!!, x, y, chartTheme.valueLineColor)
                else
                    drawLine(prevX!!, prevY!!, x, y, chartTheme.valueLineColor)
            }
            prevX = x
            prevY = y
        }
    }


    fun drawRect(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, opacity: Float = 1.0f) {

//        println(
//            """drawRect
//                $x1
//                $x2
//                $y1
//                $y2
//                ${x2 - x1}
//                ${y2 - y1}
//
//            """.trimIndent()
//        )
        drawScope?.drawRect(
            color = color.copy(alpha = opacity),
            topLeft = Offset(x = x1, y = y1),
            size = Size((x2 - x1), (y2 - y1))
        )
    }

    fun drawHorizontalLine(horizontal: Tuple4<String, Float, Long, Long>) {
        //  println("ChartView drawHorizontalLine")
        val y = priceToCoordinate(horizontal.second)
        val x1 = timeToCoordinate(horizontal.third)
        val x2 = timeToCoordinate(horizontal.fourth)
        drawLine(x1, y, x2, y, chartTheme.horizontalLineColor)
        //  val rect = Rect()
        val priceString = horizontal.second.printFloat(DECIMAL_POINTS)
        // priceTextPaint.getTextBounds(priceString, 0, priceString.length, rect)
        drawRect(priceLineX, y - crossHairTextHeight.div(2), canvasWidth, y + crossHairTextHeight.div(2), Color(0xff363A45))
        drawText(priceString, canvasWidth/* + (priceTextPaint.ascent() * 1)*/, y /*- rect.exactCenterY()*/, "#C1E1AD")
        drawText(horizontal.first, 0.0f, y /*- (timeTextPaint.ascent()) + (timeLineHeight / 3)*/, "#ffffff")


    }


    fun drawCandles() {
        // println("ChartView drawCandles")
        val x0 = timeToCoordinate(t[0])
        val x1 = timeToCoordinate(t[1])
        val candleWidthHalf = ((x0 - x1) / 3.0f).absoluteValue
        t.forEachIndexed { index, time ->
            val isGreen = c[index] >= o[index]
            val x = timeToCoordinate(t[index])
            val hY = priceToCoordinate(h[index])
            val lY = priceToCoordinate(l[index])
            val cy = priceToCoordinate(c[index])
            val oy = priceToCoordinate(o[index])
            drawRect(x - .5f, hY, x + .5f, lY, if (isGreen) chartTheme.greenCandleColor else chartTheme.redCandleColor, opacity = 1.0f)
            if (cy == oy) {
                drawRect(
                    x - candleWidthHalf,
                    oy - .5f,
                    x + candleWidthHalf,
                    cy + .5f,
                    if (isGreen) chartTheme.greenCandleColor else chartTheme.redCandleColor,
                    opacity = 1.0f
                )
            } else {
                if (isGreen) {
                    drawRect(
                        x - candleWidthHalf,
                        cy,
                        x + candleWidthHalf,
                        oy,
                        chartTheme.greenCandleColor,
                        opacity = 1.0f
                    )
                } else {
                    drawRect(
                        x - candleWidthHalf,
                        oy,
                        x + candleWidthHalf,
                        cy,
                        chartTheme.redCandleColor,
                        opacity = 1.0f
                    )
                }


            }


        }

    }


    var crossHairCallBack: ((float: Float) -> Unit)? = null

    fun drawCrossHair() {
        //  println("ChartView drawCrossHair")
        if (crossHairVisible) {
            val price = coordinateToPrice(crossHairY)
            val priceRounded = roundToTickPrice(price)
            val roundedCrossHairY = priceToCoordinate(priceRounded)
            // val rect = Rect()
            val priceString = priceRounded.printFloat(DECIMAL_POINTS)
            val priceFloat = priceString.toFloat()
            if (crossHairVisible)
                crossHairCallBack?.invoke(priceFloat)
            if (crossHairClickedPrice != null)
                drawText(percentageDifferenceString(crossHairClickedPrice!!, priceFloat) + "%", 20.0f, 20.0f, "white")


            //    priceTextPaint.getTextBounds(priceString, 0, priceString.length, rect)
            drawLineDotted(0.0f, roundedCrossHairY, canvasWidth, roundedCrossHairY, chartTheme.crossHairColor)
            drawRect(
                priceLineX,
                roundedCrossHairY - crossHairTextHeight.div(2),
                canvasWidth,
                roundedCrossHairY + crossHairTextHeight.div(2),
                Color(0x363A45)
            )
            drawText(
                priceString,
                priceLineX + 5,
                roundedCrossHairY /*- rect.exactCenterY()*/,
                "#C1E1AD", "LEFT"
            )

            val time = coordinateTotime(crossHairX)
            val roundedTime = (time / 60) * 60
            val roundedCrossHairX = timeToCoordinate(roundedTime)
            drawLineDotted(roundedCrossHairX, 0.0f, roundedCrossHairX, canvasHeight, chartTheme.crossHairColor)
            drawRect(
                roundedCrossHairX - crossHairTimeWidth.div(2),
                timeLineY,
                roundedCrossHairX + crossHairTimeWidth.div(2),
                canvasHeight,
                Color(0xff363A45)
            )
            drawText(
                milliToHSDM(roundedTime.times(1000)),
                roundedCrossHairX,
                canvasHeight - timeLineHeight /*- (timeTextPaint.ascent()) */ + (timeLineHeight / 3),
                "#C1E1AD"
            )


//            gc.setStroke(Color.web("white"));
//            gc.setLineWidth(1.0f);
//            gc.setLineDashes(0.0f);
//            gc.strokeOval(100.0f0, 100.0f, 50.0f, 50.0f);
//
//            gc.setFill(Color.web("white"))
//            gc.setLineWidth(1.0f);
//            gc.setLineDashes(0.0f);
//            gc.fillOval(200.0f0, 200.0f, 50.0f, 50.0f);


        }
    }


    fun drawLTPLine() {
        // println("ChartView drawLTPLine")

        val price = c.last()
        val priceRounded = roundToTickPrice(price)
        val y = priceToCoordinate(priceRounded)
        val priceString = priceRounded.printFloat(DECIMAL_POINTS)
        drawLine(0.0f, y, canvasWidth - priceLineWidth, y, Color(0xffC1E1AD))
        drawRect(priceLineX, y - crossHairTextHeight.div(2), canvasWidth, y + crossHairTextHeight.div(2), Color(0xffC1E1AD))
        drawText(priceString, priceLineX + 5, y, "black")

    }


    fun drawOrderLines() {
        //  println("ChartView drawLTPLine")
        orderLines.forEach {
            val price = it.second
            val priceRounded = roundToTickPrice(price)
            val y = priceToCoordinate(priceRounded)
            //  val rect = Rect()
            val text = it.first
            //   lTPTextPaint.getTextBounds(text, 0, text.length, rect)
            drawLine(0.0f, y, canvasWidth - priceLineWidth, y, it.third)
            drawText(
                text,
                canvasWidth - priceLineWidth/* + (lTPTextPaint.ascent() * .1)*/,
                y /*- (rect.exactCenterY() * 2)*/,
                "white"
            )
            drawText(
                priceRounded.printFloat(DECIMAL_POINTS),
                canvasWidth - priceLineWidth /*+ (lTPTextPaint.ascent() * .1)*/,
                y /*+ (rect.exactCenterY() * 1)*/,
                "white"
            )
        }


    }


    fun drawNotificationLines() {
        // println("drawNotificationLines")
        notificationLines.forEachIndexed { index, price ->
            val priceRounded = roundToTickPrice(price)
            val y = priceToCoordinate(priceRounded)
            //     val rect = Rect()
            val text = "\uD83D\uDD14"
            //   lTPTextPaint.getTextBounds(text, 0, text.length, rect)
            drawLine(
                0.0f,
                y,
                canvasWidth - priceLineWidth,
                y,
                if (index == notificationLinesIndexToBeDeleted) Color.Cyan else Color.Yellow
            )
            drawText(
                text,
                canvasWidth - priceLineWidth /*+ (lTPTextPaint.ascent() * .1)*/,
                y /*- (rect.exactCenterY() * .5f)*/,
                "white"
            )
        }


    }


    var _zkpmsdubgt by remember {
        mutableStateOf(0)
    }

    fun invalidate() {
        println("invalidate called from "+Thread.currentThread().getStackTrace().map { it.lineNumber }.take(5).joinToString())
        _zkpmsdubgt++
    }

    LaunchedEffect(key1 = "", block = {
        val response = withContext(Dispatchers.IO) {
            //getBhavAll()

            MCCSH.fetchSavedOrOnline(
                "2",
                WorkingDayHelper.getTodayOrLastTradingDayBefore9AM().timeInMillis,
                true
            )
        }

        priceTop = response?.h!!.maxOf { it.toFloat() }
        priceBottom = response?.l!!.minOf { it.toFloat() }
        timeStart = response.t.first()
        timeEnd = response.t.last()
        t = response.t
        h = response.h.map { it.toFloat() }.toMutableList()
        l = response.l.map { it.toFloat() }.toMutableList()
        c = response.c.map { it.toFloat() }.toMutableList()
        o = response.o.map { it.toFloat() }.toMutableList()
        response.calculateVWapHighLowDoblesTan()
        // setAddValueLines("vwap", response.vwap)
        //postInvalidate()
        //todo
        invalidate()

    })



    println("BOX_CANVAS_RUNNING")


    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
        scale *= zoomChange
        rotation += rotationChange
        offset += offsetChange

        println("zoomChange $zoomChange rotationChange $rotationChange offsetChange $offsetChange")


        val d = (timeStart - timeEnd).div(32).absoluteValue
        if (zoomChange < 1) {
            timeStart -= d
            timeEnd += d
        } else {
            timeStart += d
            timeEnd -= d
        }

        invalidate()
    }


    Box(modifier = Modifier.background(Color(0xff2B2B2B))) {
        Canvas(modifier = Modifier.fillMaxSize()
//            .pointerMoveFilter(
//                onMove = {
//                    println("onMove")
//                    false
//                }, onEnter = { println("onEnter");false }, onExit = { println("onExit");false })

//            .graphicsLayer(
//                scaleX = scale,
//                scaleY = scale,
//                rotationZ = rotation,
//                translationX = offset.x,
//                translationY = offset.y
//            )


//            .transformable(state)
            .pointerInput(Unit) {


                awaitPointerEventScope {


                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.first().position
                        // on every relayout Compose will send synthetic Move event,
                        // so we skip it to avoid event spam
//                        println("===========")
//                        if ( event.type == PointerEventType.Scroll)
//                        println("$event")


                        if (isDesktop) {
                            crossHairX = position.x
                            crossHairY = position.y
                            if (event.type == PointerEventType.Exit) {
                                crossHairVisible = false
                                invalidate()
                            } else if (event.type == PointerEventType.Enter) {
                                crossHairVisible = true
                                invalidate()
                            } else if (event.type == PointerEventType.Move) {
                                if (isDragging) {
                                    priceTop = dragStartedPriceTop - ((dragStartedY - position.y) / coordinatePriceRatio)
                                    priceBottom = dragStartedPriceBottom - ((dragStartedY - position.y) / coordinatePriceRatio)
                                    timeStart = dragStartedStartTime - ((dragStartedX - position.x) / coordinateTimeRatio).toLong()
                                    timeEnd = dragStartedEndTime - ((dragStartedX - position.x) / coordinateTimeRatio).toLong()
                                } else if (isPriceZooming) {
                                    val zoom = (priceLineZoomStartedY - position.y) / canvasHeight
                                    if (zoom >= 0) {
                                        priceTop = priceLineZoomStartedTop - (zoom.absoluteValue * zoomMax)
                                        priceBottom = priceLineZoomStartedBottom + (zoom.absoluteValue * zoomMax)
                                    } else {
                                        priceTop = priceLineZoomStartedTop + (zoom.absoluteValue * zoomMax)
                                        priceBottom = priceLineZoomStartedBottom - (zoom.absoluteValue * zoomMax)
                                    }
                                }
                                invalidate()
                            } else if (event.type == PointerEventType.Press) {
                                if (position.x >= priceLineX) {
                                    priceLineZoomStartedTop = priceTop
                                    priceLineZoomStartedBottom = priceBottom
                                    priceLineZoomStartedY = position.y
                                    zoomMax = priceTop - ((priceTop + priceBottom) / 2)
                                    isPriceZooming = true
                                } else {
                                    dragStartedPriceTop = priceTop
                                    dragStartedPriceBottom = priceBottom
                                    dragStartedStartTime = timeStart
                                    dragStartedEndTime = timeEnd
                                    dragStartedX = position.x
                                    dragStartedY = position.y
                                    isDragging = true
                                    invalidate()
                                }


                            } else if (isDragging && event.type == PointerEventType.Release) {
                                isDragging = false
                            } else if (isPriceZooming && event.type == PointerEventType.Release) {
                                isPriceZooming = false
                            } else if (event.type == PointerEventType.Scroll) {
                                if (event.changes.first().scrollDelta.x == 0f) {
                                    val d = (timeStart - timeEnd).div(16).absoluteValue
                                    if (coordinateTimeRatio.absoluteValue >= .01f && event.changes.first().scrollDelta.y >= 0) {
                                        timeStart -= d
                                        timeEnd += d
                                    } else if (d > 6) {
                                        timeStart += d
                                        timeEnd -= d
                                    }
                                    invalidate()
                                }
                            }


                        } else /*android*/ {


                            println(

                            )




                            println("event.changes ${event.changes.size} ${event.type} ${event.changes[0].uptimeMillis - event.changes[0].previousUptimeMillis} ")








                            if (pinchZoomStarted && event.type == PointerEventType.Release) {
                                finger1ID = -1
                                finger0ID = -1
                                gestureStatusReset_DESKTOP() // for remove pinch zoom
                            } else if (!pinchZoomStarted && event.changes.size == 2 && event.type == PointerEventType.Press) {
                                finger0ID = event.changes.get(0).id.value
                                finger1ID = event.changes.get(1).id.value
                                pinchStartedDistance = null
                                gestureStatusReset_DESKTOP()
                                pinchZoomStarted = true
                            } else if (pinchZoomStarted) {

                                println("==== ${event.changes}")

                                if (event.changes.size == 1) {

                                    if (event.changes[0].id.value % 2 == 0L) {
                                        pinchX0 = event.changes[0].position.x
                                        pinchY0 = event.changes[0].position.y
                                    }


                                    if (event.changes[0].id.value % 2 == 1L) {
                                        pinchX1 = event.changes[0].position.x
                                        pinchY1 = event.changes[0].position.y
                                    }


                                } else if (event.changes.size == 2) {


                                    if (event.changes[0].id.value % 2 == 0L) {
                                        pinchX0 = event.changes[0].position.x
                                        pinchY0 = event.changes[0].position.y
                                    }


                                    if (event.changes[0].id.value % 2 == 1L) {
                                        pinchX1 = event.changes[0].position.x
                                        pinchY1 = event.changes[0].position.y
                                    }
                                    if (event.changes[1].id.value % 2 == 0L) {
                                        pinchX0 = event.changes[1].position.x
                                        pinchY0 = event.changes[1].position.y
                                    }



                                    if (event.changes[1].id.value % 2 == 1L) {
                                        pinchX1 = event.changes[1].position.x
                                        pinchY1 = event.changes[1].position.y
                                    }


                                }



                                if (pinchX0 != null && pinchX1 != null && pinchY0 != null && pinchY1 != null) {
                                    // d = √[(x2 x 2 − x1 x 1 )2 + (y2 y 2 − y1 y 1 )2].
                                    val xd = (pinchX1!! - pinchX0!!).toDouble()
                                    val yd = (pinchY1!! - pinchY0!!).toDouble()
                                    val d = Math.sqrt(((xd * xd) + (yd * yd)))

                                    if (pinchStartedDistance == null) {
                                        pinchStartedDistance = d.toFloat()
                                        pinchStartedTimeStart = timeStart
                                        pinchStartedTimeEnd = timeEnd
                                    } else {

                                        val ratio = pinchStartedDistance!! / d
                                        var timeGridUnit = (pinchStartedTimeEnd - pinchStartedTimeStart) / 3L
                                        val scroll = (ratio * timeGridUnit).toLong() - timeGridUnit
                                        timeStart = pinchStartedTimeStart - scroll
                                        timeEnd = pinchStartedTimeEnd + scroll
                                        invalidate()


                                    }


                                }


                            } else {
                                if (!crossHairIsWaiting &&!pinchZoomStarted && event.type == PointerEventType.Press) {
                                    crossHairIsWaiting = true
                                    GlobalScope.launch {
                                        delay(crossHairLongPressThreshold)
                                        if (crossHairIsWaiting&&!pinchZoomStarted) {
                                            crossHairIsWaiting = false
                                            crossHairX = position.x
                                            crossHairY = position.y
                                            crossHairPressX = position.x
                                            crossHairPressY = position.y
                                            crossHairXWhenPress = crossHairX
                                            crossHairYWhenPress = crossHairY
                                            gestureStatusReset_DESKTOP()
                                            crossHairVisible = true
                                            invalidate()
                                        }

                                    }

                                }


                                if (crossHairIsWaiting && event.type != PointerEventType.Press) {
                                    crossHairIsWaiting = false
                                    if (crossHairVisible && event.type == PointerEventType.Release) {
                                        gestureStatusReset_DESKTOP() // for remove cross hair
                                        invalidate()
                                    }
                                }

                                if (crossHairVisible && event.type == PointerEventType.Press) {
                                    crossHairPressX = position.x
                                    crossHairPressY = position.y
                                    crossHairXWhenPress = crossHairX
                                    crossHairYWhenPress = crossHairY
                                } else if (crossHairVisible && event.type == PointerEventType.Move) {
                                    crossHairX = crossHairXWhenPress + (position.x - crossHairPressX)
                                    crossHairY = crossHairYWhenPress + (position.y - crossHairPressY)
                                    invalidate()
                                }


                                if (!crossHairVisible && event.type == PointerEventType.Press) {

                                    if (position.x<priceLineX){
                                        gestureStatusReset_DESKTOP()
                                        isDragging=true
                                        dragStartedPriceTop = priceTop
                                        dragStartedPriceBottom = priceBottom
                                        dragStartedStartTime = timeStart
                                        dragStartedEndTime = timeEnd
                                        dragStartedX = position.x
                                        dragStartedY = position.y
                                    }else{
                                        gestureStatusReset_DESKTOP()
                                        isPriceZooming=true
                                        priceLineZoomStartedTop = priceTop
                                        priceLineZoomStartedBottom = priceBottom
                                        priceLineZoomStartedY = position.y
                                        zoomMax = priceTop - ((priceTop + priceBottom) / 2)
                                    }


                                } else if (isDragging && event.type == PointerEventType.Move) {
                                    priceTop = dragStartedPriceTop - ((dragStartedY - position.y) / coordinatePriceRatio)
                                    priceBottom = dragStartedPriceBottom - ((dragStartedY - position.y) / coordinatePriceRatio)
                                    timeStart = dragStartedStartTime - ((dragStartedX - position.x) / coordinateTimeRatio).toLong()
                                    timeEnd = dragStartedEndTime - ((dragStartedX - position.x) / coordinateTimeRatio).toLong()
                                    invalidate()

                                } else if (isPriceZooming && event.type == PointerEventType.Move) {
                                    val zoom = (priceLineZoomStartedY - position.y) / canvasHeight
                                    if (zoom >= 0) {
                                        priceTop = priceLineZoomStartedTop - (zoom.absoluteValue * zoomMax)
                                        priceBottom = priceLineZoomStartedBottom + (zoom.absoluteValue * zoomMax)
                                    } else {
                                        priceTop = priceLineZoomStartedTop + (zoom.absoluteValue * zoomMax)
                                        priceBottom = priceLineZoomStartedBottom - (zoom.absoluteValue * zoomMax)
                                    }
                                   invalidate()

                                }

                            }


                        }


                    }
                }


            }) {


            _zkpmsdubgt.let { inv ->
                drawScope = this
                canvasWidth = size.width.toFloat()
                canvasHeight = size.height.toFloat()
                priceLineX = canvasWidth - priceLineWidth
                timeLineY = canvasHeight - timeLineHeight

                drawLine(canvasWidth - priceLineWidth, 0.0f, canvasWidth - priceLineWidth, canvasHeight, Color.Gray)
                drawLine(0.0f, canvasHeight - timeLineHeight, canvasWidth, canvasHeight - timeLineHeight, Color.Gray)
                if (t.isNotEmpty()) {
                    val priceGridUnit = (priceTop - priceBottom) / 10f
                    var timeGridUnit = (timeEnd - timeStart) / 3L
                    coordinatePriceRatio = canvasHeight / (priceTop - priceBottom)
                    coordinateTimeRatio = (canvasWidth) / (timeStart - timeEnd)
                    drawPriceGridAndTexts(priceGridUnit) //todo optimize : round
                    drawTimeGridsAndTexts(timeGridUnit) //todo optimize : round
                    valueLines.forEach {
                        drawLinesOnValues(it.second) //todo Line Names
                    }
                    horizontalLines.forEach {
                        drawHorizontalLine(it)
                    }

                    drawCandles()
                    drawOrderLines()
                    drawNotificationLines()
                    drawLTPLine()
                    drawCrossHair()
                }
                calculatePriceLineWidth()
            }


        }
    }


}




