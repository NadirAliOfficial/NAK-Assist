package com.teamnak.nakassist

object Templates {

    val list = listOf(
        Template("Thank You", "Thank you so much for your order! I'll get started right away and deliver high-quality work within the agreed timeframe. Feel free to message me if you have any questions."),
        Template("Delay Notice", "I wanted to let you know that I'm working hard on your project. I may need a little extra time to ensure the quality meets your expectations. I'll keep you updated."),
        Template("Need More Info", "Thank you for your order! To get started, could you please provide more details about your requirements? The more information you share, the better I can deliver exactly what you need."),
        Template("Revision Ready", "I've completed the revisions based on your feedback. Please review and let me know if everything looks good or if you'd like any further adjustments."),
        Template("Delivered", "I'm pleased to deliver your completed order! Please review it and don't hesitate to reach out if you need any modifications. I hope it exceeds your expectations!"),
        Template("Custom Offer", "Thank you for reaching out! Based on your requirements, I'd love to help you. I've sent a custom offer that covers everything you need. Let me know if you have any questions."),
        Template("Not Available", "Thank you for considering me for your project. Unfortunately, I'm fully booked at the moment. I'd be happy to work with you in the future — please feel free to reach out again soon."),
        Template("Ask for Review", "I'm glad I could help with your project! If you're happy with the work, I'd really appreciate a positive review. It means a lot and helps me grow on Fiverr. Thank you!")
    )

    data class Template(val title: String, val text: String)
}
