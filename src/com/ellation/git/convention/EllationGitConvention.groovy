package com.ellation.git.convention

import com.ellation.git.convention.rule.CapitalizedSubjectRule
import com.ellation.git.convention.rule.CompanyEmailDomainRule
import com.ellation.git.convention.rule.GitConventionRule
import com.ellation.git.convention.rule.JiraTicketMatchesBranchNameRule
import com.ellation.git.convention.rule.NewLineAfterSubjectRule
import com.ellation.git.convention.rule.NoPeriodAtSubjectEndRule
import com.ellation.git.convention.rule.NoPrecedingSpacesSubjectRule
import com.ellation.git.convention.rule.SubjectLengthHardLimitRule

/**
 * Represents Ellation Git convention. All rules are mandatory.
 */
class EllationGitConvention extends GitConvention {
    @Override
    List<GitConventionRule> rules() {
        return Arrays.asList(
                new CompanyEmailDomainRule(),
                new SubjectLengthHardLimitRule(),
                new CapitalizedSubjectRule(),
                new NewLineAfterSubjectRule(),
                new NoPeriodAtSubjectEndRule(),
                new NoPrecedingSpacesSubjectRule(),
                new JiraTicketMatchesBranchNameRule()
        )
    }
}
