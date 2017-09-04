import java.io.File

class GCodeProcessor {

    companion object {
        const val TOOL_CHANGE_CMD = "M117"
        const val STEPPER_TURNOFF_CMD = "M84"
    }

    private var originalLines: List<String>? = null
    private val setupSection = mutableListOf<String>()
    private val toolChangeSection = mutableListOf<String>()
    private val toolSections = mutableListOf<MutableList<String>>()
    private val finishSection = mutableListOf(STEPPER_TURNOFF_CMD)
    private var minCoords = mutableListOf(-1f, -1f)
    private val maxCoords = mutableListOf(-1f, -1f)

    private val customToolChangeSection = mutableListOf(
            ";Custom tool change",
            "G1 Z40 F2000",
            "G1 X0 Y0 F2000",
            "M117 Change Tool",
            "M25",
            "G1 Z35 F400",
            "G1 Z40 F400",
            "G1 Z35 F400",
            "G1 Z40 F400"
    )

    fun read(relativePath: String?) {
        if (relativePath == null) {
            error("No file path provided")
        }

        val gCodeFile = File(relativePath)

        if (gCodeFile.exists()) {
            println("Reading file...")
            originalLines = gCodeFile.readLines()

        } else {
            error("Gcode file doesn't exist")
        }

        println("Lines: ${originalLines?.size}")

        prep()

        println("---Setup---")
        setupSection.forEach {
            println(it)
        }

        println("---Tool Sections---")
        toolSections.forEachIndexed { index, section ->
            println("Tool ${index + 1}: ${section.size} lines")
            println(">>> ${section[0]}")
            println(">>> ${section[1]}")
            println(">>> ${section[2]}")
        }

        println("---Tool Change---")
        println(">>> ${toolChangeSection[0]}")
        println(">>> ${toolChangeSection[1]}")
        println(">>> ${toolChangeSection[2]}")

        println("---Stats---")
        println("Original Min Coords: [${minCoords[0]}, ${minCoords[1]}]")
        println("Original Max Coords: [${maxCoords[0]}, ${maxCoords[1]}]")
    }

    private fun prep() {
        if (originalLines == null) return
        setupSection.clear()
        toolSections.clear()
        var setupParsed = false
        var currentToolSection = mutableListOf<String>()
        var splitLine = listOf<String>()
        var splitSection: String? = null
        var x: Float? = null
        var y: Float? = null

        for (line in originalLines!!) {

            // save setup; assume G1 is the first motion-based command
            if (!setupParsed) {
                if (!line.startsWith("G1")) {
                    setupSection.add(line)
                    continue
                } else {
                    setupParsed = true
                }
            }

            // split into tool sections
            if (line.endsWith("T1") || line.endsWith("T2")) {
                toolChangeSection.add(line)
                if (line.startsWith(TOOL_CHANGE_CMD)) {
                    toolSections.add(currentToolSection)
                    currentToolSection = mutableListOf()
                }
                continue
            }

            // strip out anything but G* commands
            if (!line.startsWith("G")) {
                continue
            }

            currentToolSection.add(line)

            splitLine = line.split(" ")
            x = null
            y = null

            for (section in splitLine) {
                if (section.startsWith("X")) {
                    splitSection = section.substring(1)
                    x = splitSection.toFloatOrNull()
                } else if (section.startsWith("Y")) {
                    splitSection = section.substring(1)
                    y = splitSection.toFloatOrNull()
                }
            }

            if (x != null) {
                if ((x < minCoords[0] || minCoords[0] == -1f)) {
                    minCoords[0] = x
                } else if (x > maxCoords[0]) {
                    maxCoords[0] = x
                }
            }

            if (y != null) {
                if (y < minCoords[1] || minCoords[1] == -1f) {
                    minCoords[1] = y
                } else if (y > maxCoords[1]) {
                    maxCoords[1] = y
                }
            }
        }

        if (currentToolSection.size > 0) {
            toolSections.add(currentToolSection)
        }
    }

    fun duplicate(repeatX: Int, repeatY: Int, padding: Int) {
        val width = maxCoords[0] - minCoords[0]
        val height = maxCoords[1] - minCoords[1]
        toolSections.forEach { toolSection ->
            val duplicates = mutableListOf<List<String>>()
            var currX = 0
            while (currX < repeatX) {
                var currY = 0
                while (currY < repeatY) {
                    duplicates.add(toolSection.map {
                        translate(currX * (width + padding), currY * (height + padding), it)
                    })
                    currY++
                }
                currX++
            }
            toolSection.clear()
            duplicates.forEach { dupe ->
                toolSection.addAll(dupe)
            }
        }
    }

    fun generate(outputFilePath: String) {
        val output = mutableListOf<String>()

        output.addAll(setupSection)
        output.add("\n")
        toolSections.forEachIndexed { index, section ->
            output.addAll(section)
            output.add("\n")
            if (toolSections.size > 1 && index < (toolSections.size - 1)) {
                output.addAll(customToolChangeSection)
                output.add("\n")
            }
        }
        output.addAll(finishSection)

        val outputFile = File(outputFilePath)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.createNewFile()
        outputFile.writeText(output.joinToString("\n"))
        println("Done!")
    }

    private fun translate(x: Float, y: Float, line: String): String {
        val splitSections = line.split(" ")
        val translatedSections = mutableListOf<String>()

        splitSections.forEach {
            if (it.startsWith("X")) {
                translatedSections.add("X" + String.format("%.3f", (it.substring(1).toFloat() + x)))
            } else if (it.startsWith("Y")) {
                translatedSections.add("Y" + String.format("%.3f", (it.substring(1).toFloat() + y)))
            } else {
                translatedSections.add(it)
            }
        }

        return translatedSections.joinToString(" ")
    }

}