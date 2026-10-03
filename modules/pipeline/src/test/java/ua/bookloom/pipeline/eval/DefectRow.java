package ua.bookloom.pipeline.eval;

/**
 * The judge's outcomes for one corpus case.
 *
 * @param id the case id
 * @param kind the defect family
 * @param defective the label
 * @param refused whether the judge refused the candidate (score under the pass floor) in the first run
 * @param readable whether every judge reply parsed
 * @param stable whether all repeated runs gave the same verdict
 * @param runs how many times the case was judged
 */
record DefectRow(
        String id, String kind, boolean defective, boolean refused, boolean readable, boolean stable, int runs) {}
