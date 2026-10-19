package ua.bookloom.pipeline.eval;

import java.util.List;

/**
 * A recurring term of the synthetic book with the sentences that carry it, so the generator can reach the density of
 * the real run. A sentence names its slots in braces ({@code {MR}}, {@code {PLACE}}, {@code {TIME}} — "by", "before",
 * "until" or "after" always precede a time) and a speech has no closing quotes, only its own end punctuation.
 */
// Each list is a List.of(...) and so immutable; Error Prone only inspects the declared field type.
@SuppressWarnings("ImmutableEnumChecker")
enum SequenceTopic {

    /** "Mr" with a surname. */
    MR(
            List.of(
                    "{MR} crossed {PLACE} with the patience of a man who has been disappointed before.",
                    "{MR} wrote something in the margin of the ledger and then crossed it out.",
                    "By {TIME}, {MR} had stopped pretending that he was not worried.",
                    "{MR} stood at the window, turning the {OBJ} over in his fingers, and said nothing for a long while.",
                    "Nobody in the house could say when {MR} had last slept; the lamp in his study burned until {TIME}.",
                    "{MR} counted {NUMPHRASE} before he admitted that the sum could not be right.",
                    "It was {MR} who first noticed the {ADJ} smell of sulphur drifting down from {PLACE}.",
                    "{MR} had been a magician for thirty-one years, and he disliked being surprised."),
            List.of(
                    "{MR} will hear of this before {TIME}, and you will be the one to tell him.",
                    "Have you spoken to {MR} about the {OBJ}?",
                    "I would not trust {MR} with a secret, not even a small one.",
                    "If {MR} asks, tell him the door was locked.",
                    "Where did {MR} put the {OBJ}? It was here before {TIME}.",
                    "{MR} is not a cruel man, only a tired one.",
                    "Bring this to {MR} at once, and do not stop to read it.")),

    /** "Mrs" with a surname. */
    MRS(
            List.of(
                    "{MRS} kept the household accounts in a {ADJ} hand and never forgot a debt.",
                    "{MRS} poured the tea herself, because she trusted no servant with the {OBJ} on the tray.",
                    "{MRS} looked up from her book only when the door at the end of {PLACE} closed.",
                    "Before {TIME}, {MRS} had already unlocked the {OBJ} and counted the spoons.",
                    "{MRS} said very little at dinner, but she noticed everything, down to the {ADJ} crack in the cup.",
                    "{MRS} folded the letter into four and slid it under the {OBJ}.",
                    "The whole house grew quieter whenever {MRS} entered {PLACE}.",
                    "{MRS} had a way of closing a door that made grown men check what they had said."),
            List.of(
                    "{MRS} has asked for the {OBJ} twice already today.",
                    "Ask {MRS} before you touch anything in {PLACE}.",
                    "{MRS} knows more than she says, and she says more than she should.",
                    "Tell {MRS} that supper will be late, and that I am sorry for it.",
                    "I saw {MRS} leave {PLACE} before {TIME}, and she was not alone.",
                    "{MRS} would never forgive us if the {OBJ} were broken.",
                    "Whatever {MRS} told you, it was only half the truth.")),

    /** "Ms" with a surname. */
    MS(
            List.of(
                    "{MS} arrived by {TIME} with a satchel of papers and a {ADJ} look.",
                    "{MS} read the report standing up, which was her habit when she meant to disagree.",
                    "{MS} was the only visitor allowed past {PLACE} without being searched.",
                    "Everyone agreed that {MS} was clever, and nobody could agree whether that was a comfort.",
                    "{MS} tapped the {OBJ} twice with a pencil, as though it might confess.",
                    "By {TIME}, {MS} had filled three pages with notes on the {ADJ} business of the house.",
                    "{MS} did not raise her voice; she simply stopped speaking, and the room waited.",
                    "{MS} had come all the way from {PLACE} to see the {OBJ} with her own eyes."),
            List.of(
                    "{MS} wants the whole report on her desk by {TIME}.",
                    "Did you give the {OBJ} to {MS}, or did you keep it?",
                    "{MS} is waiting in {PLACE}, and she does not like to wait.",
                    "I would not argue with {MS}; she has read every ledger in this house.",
                    "If {MS} is right, we have until {TIME} and not a minute more.",
                    "Send word to {MS}: everything is ready.",
                    "{MS} never forgets a name, so be careful which one you give her.")),

    /** "master". */
    MASTER(
            List.of(
                    "The master of the house had gone out before {TIME}, leaving the {OBJ} on the table.",
                    "Every servant knew that the master disliked a {ADJ} corridor, and kept it swept.",
                    "The old master's {OBJ} still hung above the door, though nobody dared to touch it.",
                    "In the {ADJ} light the master's study looked smaller than it had the night before.",
                    "A new master was expected at {PLACE} by {TIME}, and the whole house had been scrubbed for him.",
                    "The master's chair stood empty at the head of the table, and no one sat near it.",
                    "Nobody had told the master about the {OBJ}, and nobody meant to.",
                    "The master of the chambers kept his promises, which was rarer than kindness."),
            List.of(
                    "My master is away, and I will not speak for him.",
                    "Your master would be pleased, I think, if he could see this.",
                    "The master of this house forbade it, and you know that perfectly well.",
                    "Yes, master. I shall fetch the {OBJ} at once.",
                    "A good master does not shout; he waits until the room is silent.",
                    "Who was your master before this one? Tell me honestly.",
                    "I serve no master tonight, only my own curiosity.",
                    "Master or no master, the {OBJ} belongs to {PLACE}.",
                    "Tell your master that the debt is paid.")),

    /** "imp". */
    IMP(
            List.of(
                    "The imp drifted along {PLACE} like smoke that had forgotten which way was up.",
                    "Somewhere above the {OBJ}, an imp chuckled, and the sound had edges.",
                    "The imp held very still, as only a creature without a face can hold still.",
                    "An imp is not a servant, whatever the books say; it is a prisoner who has learned manners.",
                    "The imps of {PLACE} were said to be older than the street itself, and just as {ADJ}.",
                    "The imp traced the edge of the {OBJ} with a finger of smoke, and the edge hissed.",
                    "By {TIME}, the imp had grown bored of the room and began to count the cracks in the ceiling.",
                    "The imp watched the {OBJ} for a long time before it understood what it was watching."),
            List.of(
                    "Say your name, imp, and say it clearly.",
                    "An imp is not a dog, and I will not be whistled for.",
                    "The imp knows more about this house than the whole Council put together.",
                    "Never turn your back on an imp, however politely it asks.",
                    "Why does the imp keep staring at the {OBJ}?",
                    "Get back, imp! You have no business in {PLACE}.",
                    "Imps lie when it suits them, and they tell the truth when it hurts.",
                    "I am an imp, not a miracle worker.")),

    /** "magician". */
    MAGICIAN(
            List.of(
                    "The magician set down the {OBJ} and spread his hands, as if to show that they were empty.",
                    "A magician of the Grand Council does not hurry, and this one had not hurried in thirty years.",
                    "Two magicians waited outside {PLACE}, each pretending not to notice the other.",
                    "The magician's coat smelled of chalk, wax and old promises.",
                    "It takes a magician years to learn that fear is only another tool.",
                    "The magician bent over the {OBJ}, and his shadow stretched across {PLACE} like spilled ink.",
                    "A magician who forgets the rules does not usually get the chance to forget them twice.",
                    "Behind the {ADJ} curtain, the magician began to read aloud, very quietly."),
            List.of(
                    "A magician must always know what he cannot do.",
                    "You call yourself a magician, and you cannot draw a straight line?",
                    "No magician in {PLACE} would have risked it, and you know why.",
                    "Pay no mind to me, magician; I only came to look.",
                    "The Council will hear of this, magician, I promise you.",
                    "Every magician I have served wanted the same thing: more than he had.",
                    "A magician without his books is only a man with a good memory.")),

    /** "boy". */
    BOY(
            List.of(
                    "The boy sat on the {ADJ} stairs with the {OBJ} in his lap and refused to look up.",
                    "A boy of twelve should have been asleep long before {TIME}, but this one was wide awake.",
                    "The boy had learned the whole ritual by heart, and was proud and frightened in equal measure.",
                    "Nobody noticed the boy slip out of {PLACE} while the grown-ups argued.",
                    "The boy wiped his hands on his sleeve and tried to look as if he had done this before.",
                    "In the {ADJ} dark, the boy could hear his own heart quite clearly.",
                    "The boy kept the {OBJ} hidden beneath his coat, and walked a little too carefully.",
                    "That {ADJ} morning the boy was the first in the house to hear the bell."),
            List.of(
                    "Is everything ready, boy?",
                    "You are late, boy, and the candles are almost gone.",
                    "A boy of twelve should not be out before {TIME}.",
                    "Listen to me, boy: whatever you hear tonight, do not step outside the line.",
                    "Come here, boy, and show me your hands.",
                    "He is only a boy, and you are asking too much.",
                    "Quiet, boy. The walls in {PLACE} have ears.",
                    "Nobody asked what the boy wanted.")),

    /** "sir". */
    SIR(
            List.of(
                    "He said sir the way other people say please, with a little too much weight.",
                    "The servant bowed and said sir again, quieter this time.",
                    "Nobody had called him sir before today, and the word sat strangely on his shoulders."),
            List.of(
                    "Yes, sir. I understand perfectly.",
                    "No, sir, I did not touch the {OBJ}.",
                    "Very well, sir, but I would rather be asked.",
                    "Is that all, sir? The candles will not last.",
                    "Sir, the door was open when I came.",
                    "Thank you, sir. It will not happen again.")),

    /** "pentacle". */
    PENTACLE(
            List.of(
                    "The pentacle was chalked in white across the floor of {PLACE}, its points as sharp as razors.",
                    "A smudge in the pentacle meant the whole night's work had to begin again.",
                    "He knelt beside the pentacle and checked each point twice.",
                    "Light from the {OBJ} fell across the pentacle and made the chalk glow.",
                    "Nobody was allowed to cross the pentacle once it had been closed, not even the cat.",
                    "The pentacle had been drawn before {TIME} by a hand that was not quite steady."),
            List.of(
                    "Check the pentacle again; one point looks smudged.",
                    "The pentacle was perfect when I drew it, I swear.",
                    "Do not step on the pentacle, whatever you do.",
                    "Who drew this pentacle? It is crooked at the north point.",
                    "A pentacle does not forgive a mistake, you know.")),

    /** "circle". */
    CIRCLE(
            List.of(
                    "The circle hummed faintly, like a struck glass left to ring.",
                    "He stepped carefully around the circle, which was not yet closed.",
                    "Inside the circle the air was perfectly still, and outside it the {OBJ} rattled on its hook.",
                    "A circle is only as strong as the person who drew it, and this one had been drawn in a hurry.",
                    "The candles at the edge of the circle burned with a {ADJ} blue flame.",
                    "{LATIN}, he muttered, and the circle answered with a low note."),
            List.of(
                    "The circle is drawn, and the candles are lit.",
                    "Stay inside the circle until I say otherwise.",
                    "Who broke the circle? It was whole until {TIME}.",
                    "A circle like this one would hold a hundred spirits.",
                    "Close the circle, quickly, before it notices."));

    private final List<String> narration;
    private final List<String> speech;

    SequenceTopic(final List<String> narration, final List<String> speech) {
        this.narration = narration;
        this.speech = speech;
    }

    List<String> narration() {
        return narration;
    }

    List<String> speech() {
        return speech;
    }
}
