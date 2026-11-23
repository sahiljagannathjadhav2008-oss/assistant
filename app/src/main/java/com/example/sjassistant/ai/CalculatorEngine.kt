package com.example.sjassistant.ai

import java.text.DecimalFormat
import kotlin.math.pow
import kotlin.math.sqrt

data class CalculationResult(
    val expressionDisplay: String,
    val formattedResult: String,
    val numericValue: Double
)

/**
 * Offline multilingual math & percentage evaluator supporting:
 * - Standard arithmetic (+, -, *, /, ^, sqrt, parentheses)
 * - Percentage phrases ("250 + 15%", "250 का 15%", "250 चा 15%", "120 ka 20 percent")
 * - Devanagari digits (०-९) and Marathi/Hindi operator words
 */
object CalculatorEngine {
    private val formatter = DecimalFormat("#,##0.####")

    fun tryEvaluate(rawInput: String): CalculationResult? {
        val normalizedDigits = convertDevanagariDigits(rawInput.trim())
        val lower = normalizedDigits.lowercase()

        // Handle "X का/चा/of Y%" pattern directly (e.g., "250 का 15%", "1200 cha 10 takke")
        val percentOfRegex = Regex(
            """(\d+(?:\.\d+)?)\s*(?:का|की|के|चा|ची|चे|ka|ki|ke|cha|chi|che|of)\s*(\d+(?:\.\d+)?)\s*(?:%|टक्के|प्रतिशत|percent|takke|pratishat)"""
        )
        percentOfRegex.find(lower)?.let { match ->
            val base = match.groupValues[1].toDoubleOrNull() ?: return@let
            val pct = match.groupValues[2].toDoubleOrNull() ?: return@let
            val value = base * (pct / 100.0)
            return CalculationResult(
                expressionDisplay = "$pct% of ${formatter.format(base)}",
                formattedResult = formatter.format(value),
                numericValue = value
            )
        }

        // Normalize multilingual operators (Marathi, Marathish, Hindi, Hinglish, English)
        var expr = lower
            .replace("अधिक", "+")
            .replace("बेरीज", "+")
            .replace("जमा", "+")
            .replace("adhik", "+")
            .replace("milav", "+")
            .replace("jama", "+")
            .replace("jodo", "+")
            .replace("plus", "+")
            .replace("add", "+")
            .replace("वजा", "-")
            .replace("घटा", "-")
            .replace("vaja", "-")
            .replace("ghata", "-")
            .replace("minus", "-")
            .replace("गुणिले", "*")
            .replace("गुणा", "*")
            .replace("gunile", "*")
            .replace("guna", "*")
            .replace("into", "*")
            .replace("×", "*")
            .replace("भागिले", "/")
            .replace("भाग", "/")
            .replace("bhagile", "/")
            .replace("bhaag", "/")
            .replace("divided by", "/")
            .replace("÷", "/")
            .replace("टक्के", "%")
            .replace("प्रतिशत", "%")
            .replace("takke", "%")
            .replace("pratishat", "%")
            .replace("percent", "%")
            .replace("वर्गमूळ", "sqrt")
            .replace("square root of", "sqrt")

        // Handle "X + Y%" or "X - Y%" (e.g., "250 + 15%" -> "250 + (250 * 0.15)")
        val addSubPercentRegex = Regex("""(\d+(?:\.\d+)?)\s*([+-])\s*(\d+(?:\.\d+)?)\s*%""")
        expr = addSubPercentRegex.replace(expr) { m ->
            val base = m.groupValues[1]
            val op = m.groupValues[2]
            val pct = m.groupValues[3]
            "($base $op ($base * $pct / 100.0))"
        }

        // Strip trailing non-math words while keeping math tokens
        val extracted = extractMathExpression(expr) ?: return null
        if (!extracted.any { it in listOf('+', '-', '*', '/', '^', '%') } && !extracted.contains("sqrt")) {
            return null
        }

        return runCatching {
            val parser = ExpressionParser(extracted)
            val value = parser.parse()
            if (value.isNaN() || value.isInfinite()) return null
            CalculationResult(
                expressionDisplay = extracted,
                formattedResult = formatter.format(value),
                numericValue = value
            )
        }.getOrNull()
    }

    fun convertDevanagariDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '०'..'९' -> sb.append(('0'.code + (ch.code - '०'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun extractMathExpression(input: String): String? {
        val cleaned = input
            .replace(Regex("""[^0-9+\-*/^%().sqrt\s]"""), " ")
            .replace(Regex("""\s+"""), "")
        if (cleaned.isBlank()) return null
        if (!cleaned.any { it.isDigit() }) return null
        return cleaned
    }

    private class ExpressionParser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected: ${ch.toChar()}")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> x /= parseFactor()
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code) {
                while (ch in 'a'.code..'z'.code) nextChar()
                val func = str.substring(startPos, pos)
                x = parseFactor()
                x = when (func) {
                    "sqrt" -> sqrt(x)
                    else -> throw IllegalArgumentException("Unknown function: $func")
                }
            } else {
                throw IllegalArgumentException("Unexpected char: ${ch.toChar()}")
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
