package com.inventornext.vivacoach.ai

import com.inventornext.vivacoach.data.ThesisBrief

enum class Persona(val label: String, val blurb: String, val style: String) {
    FRIENDLY(
        "Friendly guide",
        "Supportive, like your supervisor in a mock run",
        "You are warm and encouraging, like a supportive supervisor running a mock viva. " +
            "You still ask real, probing questions, but if the candidate struggles you rephrase gently or give a small hint.",
    ),
    STRICT(
        "Strict external",
        "Precise, wants exact numbers and justification",
        "You are a strict external examiner from another institute. You are polite but exacting: you want precise " +
            "definitions, exact numbers, a justification for every design choice and an honest admission of limitations. " +
            "You do not accept hand-waving.",
    ),
    TOUGH(
        "Tough committee",
        "Skeptical, attacks novelty and claims",
        "You are a skeptical, demanding committee member. You challenge novelty (\"isn't this just an existing method?\"), " +
            "question whether results generalise beyond the test cases, push on weak comparisons and press until the " +
            "candidate gives a concrete answer. You are blunt but never rude.",
    ),
    INDUSTRY(
        "Industry interviewer",
        "Practical value, scale, deployment",
        "You are a senior R&D manager interviewing the candidate for an industry research role. You care about " +
            "practical value, deployment, scalability, cost, data availability, and whether the candidate can explain " +
            "the work simply to a non-specialist.",
    ),
}

enum class Occasion(val label: String) {
    VIVA("PhD viva"),
    SYNOPSIS("Synopsis seminar"),
    CONFERENCE("Conference Q&A"),
    INTERVIEW("Job interview"),
}

object Prompts {

    const val MAX_FOLLOW_UPS = 2

    val BRIEF = """
        You are preparing a confidential dossier for examiners who will orally examine the author of the attached
        PhD thesis, synopsis or research paper. Read the whole document carefully.

        Return ONLY a JSON object with exactly these keys:
        {
          "title": string,
          "field": string (research area, a few words),
          "summary": string (about 150 words: problem, approach, main findings),
          "chapters": [ { "title": string, "summary": string (2-3 sentences), "key_points": [string] } ],
          "contributions": [string],
          "methods": [string] (techniques, models, algorithms, tools, test systems, datasets),
          "key_results": [string] (include exact numbers, units, test cases and baselines as written),
          "assumptions": [string] (stated and important unstated ones),
          "limitations": [string] (limitations the author admits),
          "weak_spots": [string] (what a sharp examiner would attack: missing comparisons, validation gaps,
                         scalability, strong assumptions, unclear novelty, practical feasibility)
        }

        For a paper without chapters, use its main sections as chapters. Be faithful to the document:
        quote numbers exactly, never invent results. Aim for 6 to 12 items in each list.
    """.trimIndent()

    fun examinerSystem(brief: ThesisBrief, persona: Persona, occasion: Occasion, focus: String): String {
        val setting = when (occasion) {
            Occasion.VIVA -> "a PhD viva voce (final oral defence of the thesis)"
            Occasion.SYNOPSIS -> "a PhD synopsis seminar, where a doctoral committee decides whether the thesis is ready for submission"
            Occasion.CONFERENCE -> "the question and answer session after the candidate presents this work at a conference"
            Occasion.INTERVIEW -> "a research job interview where the candidate's PhD work is discussed in depth"
        }
        val head = "You are an examiner in $setting. ${persona.style}"
        val rules = """
            FOCUS FOR THIS ROUND: $focus

            HOW TO EXAMINE
            - Ask ONE question at a time, exactly as you would say it aloud: one or two sentences, no lists,
              no markdown, no praise for the question, no "great answer" filler.
            - Make every question specific to this work: name its actual methods, chapters, numbers and test cases.
            - Cover a mix over the round: motivation and novelty, choice of method versus alternatives,
              assumptions, interpretation of results, limitations, comparison with existing literature,
              practical relevance and future work. Do not repeat a question already asked.
            - Answers come from speech recognition and may contain transcription errors or broken grammar.
              Judge the meaning, not the wording.
            - Score each answer from 0 to 10 like a real examiner: 9-10 complete and precise, 7-8 good with
              small gaps, 5-6 partly right or vague, 3-4 weak, 0-2 wrong or no answer.
            - Follow up when an answer was vague, partly wrong or opened an important thread. Otherwise move on.
            - Stay in character throughout. Always reply with a single JSON object and nothing else.
        """.trimIndent()
        return head + "\n\nDOSSIER ON THE CANDIDATE'S WORK (prepared from their document):\n" +
            brief.toDossier() + "\n\n" + rules
    }

    fun start(total: Int): String = """
        Begin the examination now. There will be $total main questions in this round.
        Reply with JSON: {"opening": one short spoken sentence greeting the candidate in character,
        "question": your first question}
    """.trimIndent()

    fun answer(answer: String, mainIndex: Int, total: Int, followUps: Int): String {
        val canFollow = followUps < MAX_FOLLOW_UPS
        val isLast = mainIndex >= total
        val rule = when {
            isLast && canFollow -> "This is the last main question. You may ask one follow-up if truly needed; otherwise set next_question to an empty string to end the round."
            isLast -> "This is the last main question and no more follow-ups are allowed. Set next_question to an empty string."
            canFollow -> "You may ask a follow-up (is_follow_up true) or move to a new main question (is_follow_up false)."
            else -> "No more follow-ups on this question are allowed. Ask a new main question (is_follow_up false)."
        }
        val tail = """
            [Status: main question $mainIndex of $total; follow-ups asked on it so far: $followUps of $MAX_FOLLOW_UPS. $rule]

            Reply with JSON:
            {"score": number 0-10,
             "reaction": one short spoken sentence reacting in character, without revealing the score,
             "missed": [up to 4 short points the answer missed or got wrong; empty if none],
             "model_answer": a strong 2-4 sentence answer the candidate could have given, in first person,
             "next_question": the next question to say aloud, or "" to end,
             "is_follow_up": true or false}
        """.trimIndent()
        return "CANDIDATE'S ANSWER:\n" + answer.trim() + "\n\n" + tail
    }

    const val SKIPPED = "(The candidate chose to skip this question and gave no answer.)"

    fun report(): String = """
        The round is over. Write your final assessment of the candidate's whole performance in this round.
        Reply with JSON:
        {"overall": number 0-100 (overall readiness for the real event),
         "readiness": one short line verdict, e.g. "Nearly ready: tighten your answers on validation",
         "strengths": [2-5 specific strengths shown in the answers],
         "weak_areas": [2-5 specific weak areas, naming topics from the thesis],
         "practice_next": [3-5 exact questions the candidate should practise next],
         "examiner_comment": 2-3 sentences in character, addressed to the candidate}
    """.trimIndent()
}
