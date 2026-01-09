package com.example.vocalremover.data

class CommandsBuilder {
    fun buildSeparationCommand(inputFile: String, outputVocal: String, outputInst: String): Array<String> {
        // We need to output TWO files: Vocals and Instrumental.
        // Instrumental: "Karaoke" effect (center channel removal)
        // Vocals: Approximate by Bandpass filter (simple demo approach) or subtraction if possible.

        // Command explanation:
        // [0:a]asplit=2[v][i]; - split input into two streams
        // [i]stereotools=mlev=0.01[inst] - create instrumental from one stream
        // [v]highpass=f=200,lowpass=f=3000[voc] - create "vocals" from other stream (simple frequency filter)
        // -map [voc] outputVocal
        // -map [inst] outputInst

        return arrayOf(
            "-i", inputFile,
            "-filter_complex", "[0:a]asplit=2[v][i];[i]stereotools=mlev=0.01[inst];[v]highpass=f=200,lowpass=f=3000[voc]",
            "-map", "[voc]", outputVocal,
            "-map", "[inst]", outputInst
        )
    }

    fun buildSilenceDetectCommand(inputFile: String): Array<String> {
        return arrayOf(
            "-i", inputFile,
            "-af", "silencedetect=noise=-30dB:d=0.5",
            "-f", "null",
            "-"
        )
    }

    fun buildSliceCommand(inputFile: String, start: Float, duration: Float, outputFile: String): Array<String> {
        return arrayOf(
            "-ss", start.toString(),
            "-t", duration.toString(),
            "-i", inputFile,
            "-c", "copy",
            outputFile
        )
    }
}
