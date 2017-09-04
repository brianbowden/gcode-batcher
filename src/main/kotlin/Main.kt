class Main {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val filePath = args.getOrNull(0)
            val repeatX = args.getOrNull(1)
            val repeatY = args.getOrNull(2)
            val padding = args.getOrNull(3)
            val outputFilePath = args.getOrNull(4)

            if (filePath == null || repeatX == null || repeatY == null || padding == null || outputFilePath == null) {
                println("Missing arguments. Args: [filePath repeatX repeatY padding outputFilePath]")
                return
            }

            val processor = GCodeProcessor()
            processor.read(filePath)
            processor.duplicate(repeatX.toInt(), repeatY.toInt(), padding.toInt())
            processor.generate(outputFilePath)
        }
    }
}