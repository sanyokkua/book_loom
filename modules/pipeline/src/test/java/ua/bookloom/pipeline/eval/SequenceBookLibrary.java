package ua.bookloom.pipeline.eval;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The original sentences and slot values the synthetic sequence book is built from. Nothing here quotes a real book. A
 * slot is a word in braces; {@code SequenceBookGenerator} fills it from the pool of the same name.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SequenceBookLibrary {

    static final List<String> OBJ = List.of(
            "candle",
            "brass key",
            "ledger",
            "lantern",
            "iron bell",
            "velvet curtain",
            "silver ring",
            "locked chest",
            "ink bottle",
            "cracked mirror",
            "pocket watch",
            "wax seal",
            "oak door",
            "tin cup",
            "leather satchel",
            "copper bowl");

    static final List<String> ADJ = List.of(
            "cold",
            "narrow",
            "crooked",
            "silent",
            "damp",
            "sooty",
            "heavy",
            "pale",
            "restless",
            "faint",
            "bitter",
            "windowless",
            "mouldering",
            "slow");

    static final List<String> PLACE = List.of(
            "the Great Hall",
            "the Old Bailey",
            "Kingsmarch Road",
            "Westbridge",
            "the cellar stairs",
            "the east gallery",
            "High Street",
            "Stoke-on-Marsh",
            "the Grand Council chamber",
            "the river wall",
            "the west gate",
            "Pennyfeather Lane");

    static final List<String> TIME = List.of(
            "4:30",
            "March 14th",
            "the autumn of 1887",
            "nine o'clock",
            "1904",
            "dawn",
            "midnight",
            "a quarter past six",
            "the 3rd of June",
            "the winter of 1899");

    static final List<String> NUMPHRASE = List.of(
            "3,000 candles",
            "twelve brass keys",
            "47 steps",
            "1,200 pages",
            "nine locked doors",
            "208 names",
            "fourteen crooked windows",
            "£12 in loose coins",
            "6,500 bricks");

    static final List<String> NAME = List.of(
            "Nathaniel",
            "Nathaniel",
            "Nathaniel",
            "Bartimaeus",
            "Bartimaeus",
            "Bartimaeus",
            "Underwood",
            "Whitlock",
            "Quill");

    static final List<String> MR = List.of("Mr Underwood", "Mr Underwood", "Mr Whitlock", "Mr Quill");

    static final List<String> MRS = List.of("Mrs Underwood", "Mrs Underwood", "Mrs Harrowgate");

    static final List<String> MS = List.of("Ms Lovelace", "Ms Lovelace", "Ms Pennyfeather");

    static final List<String> SPEAKER = List.of(
            "Nathaniel",
            "Bartimaeus",
            "{MR}",
            "{MRS}",
            "{MS}",
            "the boy",
            "the magician",
            "the imp",
            "Whitlock",
            "Quill");

    static final List<String> LATIN = List.of(
            "*Fiat lux*",
            "*Nihil sine labore*",
            "*Per ignem, per umbram*",
            "*Memento mori*",
            "*Sub rosa*",
            "*Ad infinitum*");

    static final List<String> EM =
            List.of("*careful*", "*never*", "*precisely*", "**twice**", "*quite*", "**nothing**");

    static final List<String> SCENE3 = List.of(
            "Rain fell on {PLACE} all through the {ADJ} afternoon, and the gutters ran like small rivers.",
            "{NAME} counted {NUMPHRASE} twice, and the sum came out different each time.",
            "The {ADJ} {OBJ} lay where it had been left, and nobody dared to move it.",
            "Before {TIME}, the bell rang in {PLACE}, and every head in the house turned toward the sound.",
            "A {ADJ} wind pushed at the shutters, and the candles leaned all together like a congregation.",
            "{NAME} had walked from {PLACE} without stopping, and his boots were soaked through.",
            "Somewhere in the walls, the old pipes ticked and settled as the night cooled.",
            "The {OBJ} cast a long shadow across the floor, and the shadow seemed to listen.",
            "Since {TIME}, the whole street had smelled of smoke, and nobody had ever explained why.",
            "The clock in {PLACE} had been wrong for years, and nobody minded.",
            "{NAME} read the letter a third time, hoping that the words would change.",
            "The {ADJ} corridor smelled of wax and wet wool.",
            "Nobody spoke for a while; the {OBJ} sat on the table like a small, patient animal.",
            "The fire had burned low, and {NAME} added a log without being asked.",
            "He had been told to be {EM}, and for once in his life he was.",
            "Until {TIME}, the house had kept its secrets well, and then it had stopped.",
            "The {ADJ} stairs creaked under {NAME}, one step at a time, all {NUMPHRASE} of them.",
            "A {ADJ} fog lay over {PLACE}, and the lamps hung in it like drowned moons.",
            "{NAME} muttered {LATIN} under his breath, and then looked embarrassed at having said it aloud.",
            "He stammered the name twice, B-Bartimaeus, B-Bartimaeus, before it came out whole.",
            "After {TIME}, nothing in {PLACE} was quite where it had been, and the servants learned not to ask.");

    static final List<String> SCENE1 = List.of(
            "I drifted along {PLACE} and did not look back, for I was tired of being looked at.",
            "I could smell the {ADJ} smoke from the stairs, and I knew what it meant.",
            "I was never fond of {PLACE}; I had spent too many nights there with nothing to do but count the bricks.",
            "I waited for {NAME} to speak, and when he did not, I coughed, which is difficult without lungs.",
            "I thought about leaving, and then I thought about the {OBJ}, and I stayed.",
            "I was bored, and a bored spirit is a dangerous thing.",
            "I went through {PLACE} twice and I found nothing, which worried me more than finding something.",
            "I felt the {ADJ} chill of the floor through my borrowed feet.",
            "I said nothing, because I had learned long ago that silence is the cheapest weapon.",
            "I wanted to be {EM} about it, but the moment was not right.",
            "I had been in {PLACE} before, back before {TIME}, and I had not enjoyed it then either.",
            "I stood very still and listened to {NAME} breathing.",
            "I was able to see the {OBJ} from where I hung, and I did not like its shape.",
            "I leaned over the {OBJ} and read what was written there, which was a mistake.",
            "I tried to remember who had summoned me last, and I could not, which was a relief.",
            "I sat on the edge of the {ADJ} sill, swinging my legs and trying to look harmless.",
            "I was glad of the dark, for I look better in it.",
            "I could have left at any moment, but I was curious, which is my chief failing.",
            "I took the {OBJ} from the shelf and weighed it in my hand, and it weighed far too little.",
            "I slipped between the bars of the gate and I was out in {PLACE} before anyone could blink.");

    static final List<String> SPEECH = List.of(
            "I did not mean to frighten anyone.",
            "The door was open when I arrived.",
            "What time is it? I have lost count.",
            "We will begin again after {TIME}, and not before.",
            "You gave me your word, and I intend to hold you to it.",
            "Where is {NAME}? I have been looking for him all morning.",
            "{NAME} will know what to do.",
            "It is only a rumour, but rumours have a way of becoming true.",
            "Nobody should be in {PLACE} after dark.",
            "Whatever happens, do not touch the {OBJ}.",
            "There are {NUMPHRASE} in this house, and I have counted every one.",
            "Before {TIME}, this would have been unthinkable.",
            "I have never seen a night so {ADJ}.",
            "Give me the {OBJ} and I will forget I ever asked.",
            "Listen carefully. I will say this only once.",
            "The answer is no, and it will remain no.",
            "Can you hear that? Something is moving in {PLACE}.",
            "We are all in danger tonight, so please be {EM}.");

    static final List<String> NAME_SPEECH = List.of(
            "I am Bartimaeus, and I do not enjoy being kept waiting.",
            "B-Bartimaeus, is that you?",
            "Nathaniel! Nathaniel, answer me!",
            "Call me Bartimaeus, or call me nothing, but do not call me imp.",
            "Underwood has never been a spirit, and he has never been a good many things.",
            "Ask Lovelace; she knows the whole story.",
            "Whitlock sent word from {PLACE} that the road is closed.",
            "Harrowgate will not like this, but Harrowgate does not like anything.");

    static final List<String> SHORT = List.of(
            "Yes.",
            "No.",
            "Silence.",
            "\"Yes, sir.\"",
            "\"Quiet, boy.\"",
            "\"Not tonight, master.\"",
            "Nobody answered.",
            "And, a split second later, the explosion.",
            "Then, without warning, the lights failed.",
            "The candle guttered and died.",
            "Thunder rolled over Westbridge.",
            "The imp grinned.",
            "“Close the circle.”",
            "Ten minutes passed. Then twenty.",
            "Something moved in the dark.",
            "\"Who is there?\"",
            "He did not look back.",
            "The bell rang once.",
            "“Mr Quill?”",
            "It was already too late.",
            "Footsteps. Then a knock.",
            "“Do it now.”",
            "The pentacle flared white.",
            "Then, very slowly, the door opened.",
            "She nodded and said nothing.",
            "\"Never mind,\" said the boy.",
            "The old clock struck midnight.",
            "Nothing happened. Nothing at all.",
            "Rain, and more rain.",
            "\"I know.\"",
            "A long, cold minute passed.",
            "“Again,” whispered the magician.",
            "The room held its breath.",
            "He chuckled, once, without joy.",
            "“Master?”",
            "The door stood open.",
            "Eleven o'clock. Then midnight.",
            "\"Where is the key?\"",
            "She smiled, very slightly.",
            "And then, silence again, deeper than before.",
            "“Go on.”",
            "The circle held.",
            "A page turned somewhere.",
            "No one dared to speak.",
            "\"Run!\"",
            "Seconds later, the windows shattered.",
            "It smelled of smoke and chalk.",
            "“Fiat lux,” he whispered.",
            "Not a sound from the cellar.",
            "The imp snickered softly.",
            "He counted to three.",
            "\"Please, sir.\"",
            "The magician sighed.",
            "For a moment, nobody moved.",
            "“That is enough, boy.”",
            "Dawn, at last.",
            "\"No, master.\"",
            "The candle flickered once more.",
            "A cold draught crossed the floor.",
            "“Wait.”");

    static final List<String> VERSE = List.of(
            "The circle holds, the candle burns,  \nthe spirit waits, the master learns;  \n"
                    + "what binds the dark will bind the hand,  \nand none who calls can quite command.",
            "Three times the bell, three times the key,  \nthree names to bind what none can see;  \n"
                    + "the chalk will crack, the flame will bend,  \nand every summons has an end.",
            "Rain on the roofs of Westbridge falls,  \nand echoes down the empty halls;  \n"
                    + "who walks tonight beneath the moon  \nwill wish he had not come so soon.");

    static final List<String> FOOTNOTE = List.of(
            "See the Underwood ledger, vol. 2, p. 114.",
            "The Old Bailey records for 1887 give a different date.",
            "The phrase is Latin: let there be light.",
            "Here the manuscript is damaged; the missing words are guessed.",
            "Compare the account of Harrowgate in the third appendix.",
            "A later hand has written \"nonsense\" beside this passage.",
            "The Great Hall was pulled down in 1911.",
            "Whitlock disputes this reading in his letters of 1902.");

    static final List<String> NEST_PRE = List.of(
            "Then he turned to me and said,",
            "She told me once,",
            "The old man always said,",
            "I remember what Whitlock told me:");

    static final List<String> NEST_POST = List.of(
            "That was all he ever told me.",
            "He meant every word of it.",
            "Then he grinned, and that was that.",
            "But that was long ago.");

    static final List<String> TITLES = List.of(
            "The Courtyard",
            "The Spirit's Account",
            "The Reckoning",
            "Ledgers and Lamps",
            "What the Spirit Saw",
            "The Council Sits",
            "The Spirit's Reply",
            "The Last Candle");
}
