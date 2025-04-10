package net.jirayu.fortify.firewall;

import net.jirayu.fortify.config.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Service
public class FirewallManager {
    private static final Logger log = LoggerFactory.getLogger(FirewallManager.class);

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");

    private final RateLimitConfig config;

    public FirewallManager(RateLimitConfig config) {
        this.config = config;
        log.info("FirewallManager initialized for {} platform", IS_WINDOWS ? "Windows" : (IS_LINUX ? "Linux" : "Unknown"));
    }

    public void blockIp(String ip) {
        if (!config.isBlockWithFirewall()) {
            return;
        }

        if (IS_WINDOWS && "windows".equalsIgnoreCase(config.getFirewallType())) {
            blockIpWithWindowsFirewall(ip);
        } else if (IS_LINUX) {
            if ("ufw".equalsIgnoreCase(config.getFirewallType())) {
                blockIpWithUfw(ip);
            } else {
                blockIpWithIptables(ip);
            }
        } else {
            log.error("Unsupported operating system for firewall integration: {}", OS);
        }
    }

    public void unblockIp(String ip) {
        if (!config.isBlockWithFirewall()) {
            return;
        }

        if (IS_WINDOWS && "windows".equalsIgnoreCase(config.getFirewallType())) {
            unblockIpWithWindowsFirewall(ip);
        } else if (IS_LINUX) {
            if ("ufw".equalsIgnoreCase(config.getFirewallType())) {
                unblockIpWithUfw(ip);
            } else {
                unblockIpWithIptables(ip);
            }
        } else {
            log.error("Unsupported operating system for firewall integration: {}", OS);
        }
    }

    private void blockIpWithIptables(String ip) {
        try {
            String inputCommand = String.format("iptables -A INPUT -s %s -j DROP", ip);
            Process inputProcess = Runtime.getRuntime().exec(inputCommand);
            int inputExitCode = inputProcess.waitFor();

            if (inputExitCode == 0) {
                log.info("Successfully blocked IP {} in INPUT chain with iptables", ip);
            } else {
                log.error("Failed to block IP {} in INPUT chain with iptables, exit code: {}", ip, inputExitCode);
                logProcessError(inputProcess);
            }

            String forwardCommand = String.format("iptables -A FORWARD -s %s -j DROP", ip);
            Process forwardProcess = Runtime.getRuntime().exec(forwardCommand);
            int forwardExitCode = forwardProcess.waitFor();

            if (forwardExitCode == 0) {
                log.info("Successfully blocked IP {} in FORWARD chain with iptables", ip);
            } else {
                log.error("Failed to block IP {} in FORWARD chain with iptables, exit code: {}", ip, forwardExitCode);
                logProcessError(forwardProcess);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing iptables block command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void unblockIpWithIptables(String ip) {
        try {
            String inputCommand = String.format("iptables -D INPUT -s %s -j DROP", ip);
            Process inputProcess = Runtime.getRuntime().exec(inputCommand);
            int inputExitCode = inputProcess.waitFor();

            if (inputExitCode == 0) {
                log.info("Successfully unblocked IP {} from INPUT chain with iptables", ip);
            } else {
                log.error("Failed to unblock IP {} from INPUT chain with iptables, exit code: {}", ip, inputExitCode);
                logProcessError(inputProcess);
            }

            String forwardCommand = String.format("iptables -D FORWARD -s %s -j DROP", ip);
            Process forwardProcess = Runtime.getRuntime().exec(forwardCommand);
            int forwardExitCode = forwardProcess.waitFor();

            if (forwardExitCode == 0) {
                log.info("Successfully unblocked IP {} from FORWARD chain with iptables", ip);
            } else {
                log.error("Failed to unblock IP {} from FORWARD chain with iptables, exit code: {}", ip, forwardExitCode);
                logProcessError(forwardProcess);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing iptables unblock command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void blockIpWithUfw(String ip) {
        try {
            String command = String.format("ufw deny from %s to any", ip);
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Successfully blocked IP {} with UFW", ip);
            } else {
                log.error("Failed to block IP {} with UFW, exit code: {}", ip, exitCode);
                logProcessError(process);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing UFW block command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void unblockIpWithUfw(String ip) {
        try {
            String command = String.format("ufw delete deny from %s to any", ip);
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Successfully unblocked IP {} with UFW", ip);
            } else {
                log.error("Failed to unblock IP {} with UFW, exit code: {}", ip, exitCode);
                logProcessError(process);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing UFW unblock command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void blockIpWithWindowsFirewall(String ip) {
        try {
            String ruleName = "Fortify-Block-" + ip.replace(".", "-");

            String checkCommand = String.format(
                    "netsh advfirewall firewall show rule name=\"%s\"",
                    ruleName
            );

            Process checkProcess = Runtime.getRuntime().exec(checkCommand);
            int checkExitCode = checkProcess.waitFor();

            if (checkExitCode == 0) {
                unblockIpWithWindowsFirewall(ip);
            }

            String command = String.format(
                    "cmd /c netsh advfirewall firewall add rule name=\"%s\" dir=in action=block remoteip=%s/32 enable=yes",
                    ruleName, ip
            );

            String outboundCommand = String.format(
                    "cmd /c netsh advfirewall firewall add rule name=\"%s-out\" dir=out action=block remoteip=%s/32 enable=yes",
                    ruleName, ip
            );

            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Successfully blocked inbound traffic for IP {} with Windows Firewall", ip);

                Process outProcess = Runtime.getRuntime().exec(outboundCommand);
                int outExitCode = outProcess.waitFor();

                if (outExitCode == 0) {
                    log.info("Successfully blocked outbound traffic for IP {} with Windows Firewall", ip);
                } else {
                    log.error("Failed to block outbound traffic for IP {} with Windows Firewall, exit code: {}", ip, outExitCode);
                    logProcessError(outProcess);
                }
            } else {
                log.error("Failed to block inbound traffic for IP {}: Access denied. Application must be run as Administrator.", ip);
                if (IS_WINDOWS && !isRunningAsAdmin()) {
                    log.error("Please restart the application with Administrator privileges.");
                }

                logProcessError(process);

                String altCommand = String.format(
                        "cmd /c %s\\system32\\netsh advfirewall firewall add rule name=\"%s\" dir=in action=block remoteip=%s/32 enable=yes",
                        System.getenv("WINDIR"), ruleName, ip
                );

                log.info("Attempting alternative approach with command: {}", altCommand);
                Process altProcess = Runtime.getRuntime().exec(altCommand);
                int altExitCode = altProcess.waitFor();

                if (altExitCode == 0) {
                    log.info("Successfully blocked IP {} with Windows Firewall using alternative approach", ip);
                } else {
                    log.error("Failed to block IP {} with Windows Firewall using alternative approach, exit code: {}", ip, altExitCode);
                    logProcessError(altProcess);
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing Windows Firewall block command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void unblockIpWithWindowsFirewall(String ip) {
        try {
            String ruleName = "Fortify-Block-" + ip.replace(".", "-");

            String command = String.format(
                    "cmd /c netsh advfirewall firewall delete rule name=\"%s\"",
                    ruleName
            );

            String outboundCommand = String.format(
                    "cmd /c netsh advfirewall firewall delete rule name=\"%s-out\"",
                    ruleName
            );

            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            boolean inboundSuccess = false;
            if (exitCode == 0) {
                log.info("Successfully removed inbound block for IP {} from Windows Firewall", ip);
                inboundSuccess = true;
            } else {
                log.error("Failed to remove inbound block for IP {} from Windows Firewall, exit code: {}", ip, exitCode);
                logProcessError(process);

                String altCommand = String.format(
                        "cmd /c %s\\system32\\netsh advfirewall firewall delete rule name=\"%s\"",
                        System.getenv("WINDIR"), ruleName
                );

                Process altProcess = Runtime.getRuntime().exec(altCommand);
                int altExitCode = altProcess.waitFor();

                if (altExitCode == 0) {
                    log.info("Successfully removed inbound block for IP {} using alternative approach", ip);
                    inboundSuccess = true;
                } else {
                    log.error("Failed to remove inbound block for IP {} using alternative approach, exit code: {}", ip, altExitCode);
                    logProcessError(altProcess);
                }
            }

            if (inboundSuccess) {
                Process outProcess = Runtime.getRuntime().exec(outboundCommand);
                int outExitCode = outProcess.waitFor();

                if (outExitCode == 0) {
                    log.info("Successfully removed outbound block for IP {} from Windows Firewall", ip);
                } else {
                    log.error("Failed to remove outbound block for IP {} from Windows Firewall, exit code: {}", ip, outExitCode);
                    logProcessError(outProcess);
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing Windows Firewall unblock command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void logProcessError(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            StringBuilder errorOutput = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            if (errorOutput.length() > 0) {
                log.error("Process error output: {}", errorOutput.toString());
            }

            try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder stdOutput = new StringBuilder();
                while ((line = stdReader.readLine()) != null) {
                    stdOutput.append(line).append("\n");
                }
                if (stdOutput.length() > 0) {
                    log.info("Process standard output: {}", stdOutput.toString());
                }
            }
        } catch (IOException e) {
            log.error("Error reading process streams: {}", e.getMessage());
        }
    }

    private boolean isRunningAsAdmin() {
        try {
            Process process = Runtime.getRuntime().exec("reg query HKU\\S-1-5-19");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}