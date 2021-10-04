package ch.unibas.cs.gravis.thriftservice.sampling.proposals

import scalismo.sampling.{ProposalGenerator, TransitionProbability}

class PartsModelProposal[A, PART](proposal: ProposalGenerator[PART] with TransitionProbability[PART],
                                  extract: A => PART,
                                  emplace: (A, PART) => A)
        extends ProposalGenerator[A] with TransitionProbability[A] {

    override def propose(current: A): A = {
        emplace(current, proposal.propose(extract(current)))
    }

    override def logTransitionProbability(from: A, to: A): Double = {
        proposal.logTransitionProbability(extract(from), extract(to))
    }
}
