# Our Commitment to Security
The [PROJECT NAME PLACEHOLDER] team takes the security of our software seriously. We appreciate your efforts to responsibly disclose your findings and will make every effort to acknowledge your contributions.

## Supported Versions
The latest version is supported.

## Reporting a Vulnerability
Please _do NOT report security vulnerabilities in public_.

### How to Report
To report a security vulnerability, please use our [HackerOne reporting platform](https://hackerone.com/18eabc29-761d-4062-8376-5298660ac9d8/embedded_submissions/new?locale=en). All vulnerability reports will be handled according to [Hyland's guidelines](https://hackerone.com/hyland_software).

### What Happens After You Report
1. Acknowledgment
- We confirm receipt of your report
- We may ask clarifying questions

2. Validation
- We reproduce the issue
- We assess the severity and impact
- We determine affected versions

3. Development
- We develop a fix
- We create test cases
- We validate the fix internally

4. Disclosure
- We coordinate with you on disclosure timing
- We prepare a security advisory
- We release the fix

5. Public Disclosure
- We publish a security advisory
- We credit you (if you wish)
- We notify affected users

## Coordinated Disclosure
We follow responsible disclosure practices:

### Our Commitment
- We will keep you informed throughout the process
- We will credit you in the security advisory (if you wish)
- We will work with you on the disclosure timeline
- We will not take legal action against security researchers who:
    - Act in good faith
    - Follow this policy
    - Do not compromise user data or privacy

### Our Request
Please allow us:

- Adequate time to fix the vulnerability before public disclosure
- The opportunity to coordinate on disclosure timing
- The ability to prepare mitigations and user notifications

### Typical Disclosure Timeline
- 90 days: Standard disclosure timeline after initial report
- Shorter: For critical vulnerabilities actively exploited
- Longer: By mutual agreement for complex issues

## Scope
### In Scope
Security vulnerabilities in:

- [PROJECT NAME PLACEHOLDER] core code
- Official [PROJECT NAME PLACEHOLDER] extensions and plugins
- Official [PROJECT NAME PLACEHOLDER] Docker images
- [PROJECT NAME PLACEHOLDER] REST APIs
- [PROJECT NAME PLACEHOLDER] authentication and authorization
- [PROJECT NAME PLACEHOLDER] data handling and storage

### Out of Scope
The following are generally NOT considered security vulnerabilities:

- Issues in third-party dependencies (please report them to the dependency maintainer)
- Social engineering attacks
- Denial of service requiring excessive resources
- Issues requiring physical access to the server
- Vulnerabilities in outdated/unsupported versions
- Issues already known and documented
- Theoretical vulnerabilities without proof of concept

## Testing Guidelines
When testing for vulnerabilities, please:

- DO: Use your own test instances
- DO: Stop when you've proven the vulnerability exists
- DO: Respect rate limits and system resources
- DO: Clean up after testing (remove test data, accounts, etc.)
- DON'T: Test against production systems you don't own
- DON'T: Access, modify, or delete data belonging to others
- DON'T: Perform attacks that could harm availability
- DON'T: Use automated scanners without permission
- DON'T: Exploit a vulnerability beyond demonstrating it exists

## Security Best Practices
While using [PROJECT NAME PLACEHOLDER], we recommend:

### For Deployment
- Keep [PROJECT NAME PLACEHOLDER] updated to the latest stable version
- Use strong, unique passwords for all accounts
- Enable HTTPS/TLS for all communications
- Regularly backup your data
- Monitor logs for suspicious activity
- Apply the principle of least privilege
- Keep underlying infrastructure updated (OS, Java, database)

### For Configuration
- Review and harden default configurations
- Disable unnecessary features and endpoints
- Use environment variables for secrets (never hardcode)
- Enable audit logging
- Configure appropriate rate limiting
- Use a Web Application Firewall (WAF) where appropriate

### For Development
Follow secure coding practices

- Validate and sanitize all inputs
- Use parameterized queries (prevent SQL injection)
- Implement proper authentication and authorization
- Handle errors securely (don't expose sensitive info)
- Keep dependencies updated

## Security Advisories
We publish security advisories for all resolved vulnerabilities:

### Where to Find Advisories
- GitHub Security Advisories: [PLACEHOLDER: Link to GitHub Security tab]

### Advisory Format
Each advisory includes:

- CVE Identifier: When applicable
- Severity Rating: Using CVSS score
- Affected Versions: Which versions are vulnerable
- Description: What the vulnerability allows
- Fixed Versions: Which versions contain the fix
- Workarounds: Temporary mitigations (if available)
- Credits: Recognition of the reporter

### Subscribing to Advisories
To receive security notifications:

- GitHub: Click "Watch" → "Custom" → "Security alerts" on our repository

## Recognition
We believe in recognizing the valuable work of security researchers:

### Hall of Fame
We maintain a [Security Researchers Hall of Fame](https://hackerone.com/hyland_software/thanks) acknowledging contributors who have responsibly disclosed vulnerabilities.

### What You Get
- Public Recognition: listed in our Hall of Fame (if you wish)
- CVE Credit: named in the CVE and security advisory
- Direct Communication: with our security team
- Early Notification: of the fix before public disclosure

### What We Don't Offer
- We do not currently offer a bug bounty program
- We do not provide financial rewards
- We cannot guarantee job opportunities

## Legal
### Safe Harbor
Hyland supports security research and wants security researchers to feel comfortable reporting vulnerabilities. We will not pursue civil or criminal action, or send notice to law enforcement for accidental or good faith violations of this policy. We consider security research conducted consistent with this policy to be:

- Authorized concerning any application of anti-hacking laws
- Exempt from restrictions in our Terms of Service that would interfere with research
- Lawful, helpful to security, and conducted in good faith

You must:

- Comply with this policy
- Act in good faith
- Not intentionally harm users or Hyland
- Not access, modify, or exfiltrate data beyond what is necessary to demonstrate the vulnerability

## Terms & Conditions
By participating in our vulnerability disclosure program, you agree that:

- You will not exploit the vulnerability beyond the minimum necessary to prove it exists
- You will report vulnerabilities privately and will not disclose them publicly until we have had adequate time to address them
- You will comply with all applicable laws
- All information you provide will be used only for security purposes

## Resources
- [OWASP Top Ten](https://owasp.org/www-project-top-ten/)
- [Common Vulnerabilities and Exposures (CVE)](https://cve.mitre.org/)
- [Common Vulnerability Scoring System (CVSS)](https://www.first.org/cvss/)
- [HackerOne Disclosure Guidelines](https://www.hackerone.com/disclosure-guidelines)

Thank you for helping keep [PROJECT NAME PLACEHOLDER] and our users safe!
